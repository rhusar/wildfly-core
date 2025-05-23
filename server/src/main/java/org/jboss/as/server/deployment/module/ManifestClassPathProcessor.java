/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jboss.as.controller.ModuleIdentifierUtil;
import org.jboss.as.server.deployment.Attachable;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.AttachmentList;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.MountedDeploymentOverlay;
import org.jboss.as.server.deployment.SubDeploymentMarker;
import org.jboss.as.server.deployment.annotation.ResourceRootIndexer;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.moduleservice.ExternalModule;
import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VFSUtils;
import org.jboss.vfs.VirtualFile;

/**
 * A processor which adds class path entries for each manifest entry.
 * <p/>
 * <p/>
 * <p/>
 * </li>
 * <li>
 * If the Class-Path entry is external to the deployment then it is handled by the external jar service.</li>
 * <li>
 * If the entry refers to a sibling deployment then a dependency is added on that deployment. If this deployment is
 * not present then this deployment will block until it is.</li>
 * <li>
 * If the Class-Path entry points to a jar inside the ear that is not a deployment and not a /lib jar then a reference is added
 * to this jars {@link AdditionalModuleSpecification}</li>
 * </ul>
 *
 * @author Stuart Douglas
 * @author Ales Justin
 */
public final class ManifestClassPathProcessor implements DeploymentUnitProcessor {

    /**
     * Module identifiers for Class-Path information.
     */
    static final AttachmentKey<AttachmentList<String>> CLASS_PATH_MODULES = AttachmentKey.createList(String.class);

    private static final String[] EMPTY_STRING_ARRAY = {};

    /**
     * We only allow a single deployment at a time to be run through the class path processor.
     * <p/>
     * This is because if multiple sibling deployments reference the same item we need to make sure that they end up
     * with the same external module, and do not both create an external module with the same name.
     */
    public synchronized void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();


        final DeploymentUnit parent = deploymentUnit.getParent();
        final DeploymentUnit topLevelDeployment = parent == null ? deploymentUnit : parent;
        final VirtualFile topLevelRoot = topLevelDeployment.getAttachment(Attachments.DEPLOYMENT_ROOT).getRoot();
        final ExternalModule externalModuleService = topLevelDeployment.getAttachment(Attachments.EXTERNAL_MODULE_SERVICE);
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        final ServiceTarget externalServiceTarget = deploymentUnit.getAttachment(Attachments.EXTERNAL_SERVICE_TARGET);

        //These are resource roots that are already accessible by default
        //such as ear/lib jars an web-inf/lib jars
        final Set<VirtualFile> existingAccessibleRoots = new HashSet<>();

        final Map<VirtualFile, ResourceRoot> subDeployments = new HashMap<>();
        for (ResourceRoot root : DeploymentUtils.allResourceRoots(topLevelDeployment)) {
            if (SubDeploymentMarker.isSubDeployment(root)) {
                subDeployments.put(root.getRoot(), root);
            } else if (ModuleRootMarker.isModuleRoot(root)) {
                //top level module roots are already accessible, as they are either
                //ear/lib jars, or jars that are already part of the deployment
                existingAccessibleRoots.add(root.getRoot());
            }
        }

        final ArrayDeque<RootEntry> resourceRoots = new ArrayDeque<>();
        if (deploymentUnit.getParent() != null) {
            //top level deployments already had their exiting roots processed above
            for (ResourceRoot root : DeploymentUtils.allResourceRoots(deploymentUnit)) {

                if (ModuleRootMarker.isModuleRoot(root)) {
                    //if this is a sub deployment of an ear we need to make sure we don't
                    //re-add existing module roots as class path entries
                    //this will mainly be WEB-INF/(lib|classes) entries
                    existingAccessibleRoots.add(root.getRoot());
                }
            }
        }

        for (ResourceRoot root : DeploymentUtils.allResourceRoots(deploymentUnit)) {
            //add this to the list of roots to be processed
            resourceRoots.add(new RootEntry(deploymentUnit, root));
        }

        // build a map of the additional module locations
        // note that if a resource root has been added to two different additional modules
        // and is then referenced via a Class-Path entry the behaviour is undefined
        final Map<VirtualFile, AdditionalModuleSpecification> additionalModules = new HashMap<>();
        final List<AdditionalModuleSpecification> additionalModuleList = topLevelDeployment.getAttachmentList(Attachments.ADDITIONAL_MODULES);
        // Must synchronize on list as subdeployments executing Phase.STRUCTURE may be concurrently modifying it
        synchronized (additionalModuleList) {
            for (AdditionalModuleSpecification module : additionalModuleList) {
                for (ResourceRoot additionalModuleResourceRoot : module.getResourceRoots()) {
                    additionalModules.put(additionalModuleResourceRoot.getRoot(), module);
                }
            }
        }

        //additional resource roots may be added as
        while (!resourceRoots.isEmpty()) {
            final RootEntry entry = resourceRoots.pop();
            final ResourceRoot resourceRoot = entry.resourceRoot;
            final Attachable target = entry.target;

            //if this is a top level deployment we do not want to process sub deployments
            if (SubDeploymentMarker.isSubDeployment(resourceRoot) && resourceRoot != deploymentRoot) {
                continue;
            }

            final String[] items = getClassPathEntries(resourceRoot);
            for (final String item : items) {
                if (item.isEmpty() || item.equals(".")) { //a class path of . causes problems and is unnecessary, see WFLY-2725
                    continue;
                }
                //first try and resolve relative to the manifest resource root
                final VirtualFile classPathFile = resourceRoot.getRoot().getParent().getChild(item);
                //then resolve relative to the deployment root
                final VirtualFile topLevelClassPathFile = deploymentRoot.getRoot().getParent().getChild(item);
                if (item.startsWith("/")) {
                    if (externalModuleService.isValidFile(item)) {
                        final String moduleIdentifier = externalModuleService.addExternalModuleAsString(item, phaseContext.getServiceRegistry(), externalServiceTarget);
                        target.addToAttachmentList(CLASS_PATH_MODULES, moduleIdentifier);
                        ServerLogger.DEPLOYMENT_LOGGER.debugf("Resource %s added as external jar %s", classPathFile, resourceRoot.getRoot());
                    } else {
                        ServerLogger.DEPLOYMENT_LOGGER.classPathEntryNotValid(item, resourceRoot.getRoot().getPathName());
                    }
                } else {
                    if (classPathFile.exists()) {
                        //we need to check that this class path item actually lies within the deployment
                        boolean found = false;
                        VirtualFile file = classPathFile.getParent();
                        while (file != null) {
                            if (file.equals(topLevelRoot)) {
                                found = true;
                            }
                            file = file.getParent();
                        }
                        if (!found) {
                            ServerLogger.DEPLOYMENT_LOGGER.classPathEntryNotValid(item, resourceRoot.getRoot().getPathName());
                        } else {
                            handlingExistingClassPathEntry(resourceRoots, topLevelDeployment, topLevelRoot, subDeployments, additionalModules, existingAccessibleRoots, resourceRoot, target, classPathFile);
                        }
                    } else if (topLevelClassPathFile.exists()) {
                        boolean found = false;
                        VirtualFile file = topLevelClassPathFile.getParent();
                        while (file != null) {
                            if (file.equals(topLevelRoot)) {
                                found = true;
                            }
                            file = file.getParent();
                        }
                        if (!found) {
                            ServerLogger.DEPLOYMENT_LOGGER.classPathEntryNotValid(item, resourceRoot.getRoot().getPathName());
                        } else {
                            handlingExistingClassPathEntry(resourceRoots, topLevelDeployment, topLevelRoot, subDeployments, additionalModules, existingAccessibleRoots, resourceRoot, target, topLevelClassPathFile);
                        }
                    } else {
                        ServerLogger.DEPLOYMENT_LOGGER.classPathEntryNotValid(item, resourceRoot.getRoot().getPathName());
                    }
                }
            }
        }
    }

    private void handlingExistingClassPathEntry(final ArrayDeque<RootEntry> resourceRoots, final DeploymentUnit topLevelDeployment, final VirtualFile topLevelRoot, final Map<VirtualFile, ResourceRoot> subDeployments, final Map<VirtualFile, AdditionalModuleSpecification> additionalModules, final Set<VirtualFile> existingAccessibleRoots, final ResourceRoot resourceRoot, final Attachable target, final VirtualFile classPathFile) throws DeploymentUnitProcessingException {
        if (existingAccessibleRoots.contains(classPathFile)) {
            ServerLogger.DEPLOYMENT_LOGGER.debugf("Class-Path entry %s in %s ignored, as target is already accessible", classPathFile, resourceRoot.getRoot());
        } else if (additionalModules.containsKey(classPathFile)) {
            final AdditionalModuleSpecification moduleSpecification = additionalModules.get(classPathFile);
            //as class path entries are exported, transitive dependencies will also be available
            target.addToAttachmentList(CLASS_PATH_MODULES, moduleSpecification.getModuleName());
        } else if (subDeployments.containsKey(classPathFile)) {
            //now we need to calculate the sub deployment module identifier
            //unfortunately the sub deployment has not been setup yet, so we cannot just
            //get it from the sub deployment directly
            final ResourceRoot otherRoot = subDeployments.get(classPathFile);
            target.addToAttachmentList(CLASS_PATH_MODULES, ModuleIdentifierProcessor.createModuleIdentifierAsString(otherRoot.getRootName(), otherRoot, topLevelDeployment, topLevelRoot, false));
        } else {
            String identifier = createAdditionalModule(resourceRoot, topLevelDeployment, topLevelRoot, additionalModules, classPathFile, resourceRoots);
            target.addToAttachmentList(CLASS_PATH_MODULES, identifier);
        }
    }

    private String createAdditionalModule(final ResourceRoot resourceRoot, final DeploymentUnit topLevelDeployment, final VirtualFile topLevelRoot, final Map<VirtualFile, AdditionalModuleSpecification> additionalModules, final VirtualFile classPathFile, final ArrayDeque<RootEntry> resourceRoots) throws DeploymentUnitProcessingException {
        final ResourceRoot root = createResourceRoot(classPathFile, topLevelDeployment, topLevelRoot);
        final String pathName = root.getRoot().getPathNameRelativeTo(topLevelRoot);
        String identifier = ModuleIdentifierUtil.canonicalModuleIdentifier(ServiceModuleLoader.MODULE_PREFIX + topLevelDeployment.getName() + "." + pathName, null);
        AdditionalModuleSpecification module = new AdditionalModuleSpecification(identifier, root);
        topLevelDeployment.addToAttachmentList(Attachments.ADDITIONAL_MODULES, module);
        additionalModules.put(classPathFile, module);
        resourceRoot.addToAttachmentList(Attachments.CLASS_PATH_RESOURCE_ROOTS, root);

        //add this to the list of roots to be processed, so transitive class path entries will be respected
        resourceRoots.add(new RootEntry(module, root));
        return identifier;

    }

    private static String[] getClassPathEntries(final ResourceRoot resourceRoot) {

        final Manifest manifest;
        try {
            manifest = VFSUtils.getManifest(resourceRoot.getRoot());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (manifest == null) {
            // no class path to process!
            return EMPTY_STRING_ARRAY;
        }
        final String classPathString = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        if (classPathString == null) {
            // no entry
            return EMPTY_STRING_ARRAY;
        }
        return classPathString.split("\\s+");
    }

    /**
     * Creates a {@link ResourceRoot} for the passed {@link VirtualFile file} and adds it to the list of {@link ResourceRoot}s
     * in the {@link DeploymentUnit deploymentUnit}
     *
     *
     * @param file           The file for which the resource root will be created
     * @return Returns the created {@link ResourceRoot}
     */
    private synchronized ResourceRoot createResourceRoot(final VirtualFile file, final DeploymentUnit deploymentUnit, final VirtualFile deploymentRoot) throws DeploymentUnitProcessingException {
        try {
            Map<String, MountedDeploymentOverlay> overlays = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_OVERLAY_LOCATIONS);

            String relativeName = file.getPathNameRelativeTo(deploymentRoot);
            MountedDeploymentOverlay overlay = overlays.get(relativeName);
            Closeable closable = null;
            if(overlay != null) {
                overlay.remountAsZip(false);
            } else if(file.isFile()) {
                closable = VFS.mountZip(file.getPhysicalFile(), file, TempFileProviderService.provider());
            }
            final MountHandle mountHandle = MountHandle.create(closable);
            final ResourceRoot resourceRoot = new ResourceRoot(file, mountHandle);
            ModuleRootMarker.mark(resourceRoot);
            ResourceRootIndexer.indexResourceRoot(resourceRoot);
            return resourceRoot;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class RootEntry {
        private final ResourceRoot resourceRoot;
        private final Attachable target;


        private RootEntry(final Attachable target, final ResourceRoot resourceRoot) {
            this.target = target;
            this.resourceRoot = resourceRoot;
        }
    }

}
