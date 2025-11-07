/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.ExtensionRegistryType;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.wildfly.extension.io.IOExtension;
import org.wildfly.extension.io.IOSubsystemSchema;
import org.wildfly.io.IOServiceDescriptor;

@RunWith(Parameterized.class)
public class IORemotingIntegrationTestCase extends AbstractSubsystemSchemaTest<IOSubsystemSchema> {

    @Parameters
    public static Iterable<IOSubsystemSchema> parameters() {
        return EnumSet.allOf(IOSubsystemSchema.class);
    }

    public IORemotingIntegrationTestCase(IOSubsystemSchema schema) {
        super("io", new IOExtension(), schema, IOSubsystemSchema.CURRENT);
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        String namespace = getSubsystemSchema().getNamespace().getUri();
        return String.format(
                "<subsystem xmlns=\"%s\">" +
                "    <worker name=\"default\"/>" +
                "    <buffer-pool name=\"default\"/>" +
                "</subsystem>", namespace);
    }

    /**
     * Verifies that when Remoting references a non-default worker and that worker is defined,
     * the system boots correctly and the default worker is NOT instantiated.
     */
    @Test
    public void testNonDefaultWorkerWithRemoting() throws Exception {
        String namespace = getSubsystemSchema().getNamespace().getUri();
        String ioXml = String.format(
                "<subsystem xmlns=\"%s\">" +
                "    <worker name=\"my-worker\"/>" +
                "    <buffer-pool name=\"default\"/>" +
                "</subsystem>", namespace);

        // parse() goes through IOSubsystemSchema.additionalOperations - the fixed code path
        List<ModelNode> bootOps = parse(ioXml);

        ModelNode remotingAdd = Util.createAddOperation(PathAddress.pathAddress(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME));
        remotingAdd.get("worker").set("my-worker");
        bootOps.add(remotingAdd);

        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .setBootOperations(bootOps)
                .build();

        Assert.assertTrue("Boot should succeed with non-default worker: " + services.getBootError(),
                services.isSuccessfulBoot());

        ServiceController<?> defaultWorker = services.getContainer()
                .getService(ServiceName.parse(IOServiceDescriptor.WORKER.getName()).append("default"));
        Assert.assertNull("Default worker service must not be instantiated", defaultWorker);
    }

    /**
     * Verifies that when Remoting uses the default worker (no explicit worker attribute),
     * a "default" IO worker in the configuration is automatically wired and the system boots correctly.
     */
    @Test
    public void testDefaultWorkerWithRemoting() throws Exception {
        String namespace = getSubsystemSchema().getNamespace().getUri();
        String ioXml = String.format(
                "<subsystem xmlns=\"%s\">" +
                "    <worker name=\"default\"/>" +
                "    <buffer-pool name=\"default\"/>" +
                "</subsystem>", namespace);

        // parse() goes through IOSubsystemSchema.additionalOperations which sets default-worker=default
        List<ModelNode> bootOps = parse(ioXml);

        // Remoting with no explicit worker uses the IO default worker
        bootOps.add(Util.createAddOperation(PathAddress.pathAddress(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME)));

        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .setBootOperations(bootOps)
                .build();

        Assert.assertTrue("Boot should succeed with default worker: " + services.getBootError(),
                services.isSuccessfulBoot());

        ServiceController<?> defaultWorker = services.getContainer()
                .getService(ServiceName.parse(IOServiceDescriptor.WORKER.getName()).append("default"));
        Assert.assertNotNull("Default worker service must be installed", defaultWorker);
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return new AdditionalInitialization() {

            @Override
            protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry, Resource rootResource,
                    ManagementResourceRegistration rootRegistration, RuntimeCapabilityRegistry capabilityRegistry) {
                super.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration, capabilityRegistry);
                registerRemotingExtension(extensionRegistry, rootRegistration);
            }
        };
    }

    private static void registerRemotingExtension(ExtensionRegistry extensionRegistry,
            ManagementResourceRegistration rootRegistration) {
        ManagementResourceRegistration extReg = rootRegistration.registerSubModel(new SimpleResourceDefinition(
                PathElement.pathElement(EXTENSION, "org.jboss.as.remoting"),
                NonResolvingResourceDescriptionResolver.INSTANCE,
                ModelOnlyAddStepHandler.INSTANCE, ModelOnlyRemoveStepHandler.INSTANCE));
        extReg.registerReadOnlyAttribute(new SimpleAttributeDefinitionBuilder("module", ModelType.STRING).build(), null);
        new RemotingExtension().initialize(extensionRegistry.getExtensionContext(
                "org.jboss.as.remoting", rootRegistration, ExtensionRegistryType.MASTER));
    }
}
