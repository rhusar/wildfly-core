/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.server.security.SecurityMetaData.ATTACHMENT_KEY;
import static org.jboss.as.server.security.VirtualDomainMarkerUtility.isVirtualDomainRequiredByUnit;
import static org.jboss.as.server.security.VirtualDomainMarkerUtility.virtualDomainName;
import static org.jboss.as.server.security.VirtualDomainUtil.setTopLevelDeploymentSecurityMetaData;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.security.SecurityMetaData;
import org.jboss.msc.service.ServiceName;

/**
 * A {@code DeploymentUnitProcessor} to set the {@code ServiceName} of any virtual security domain to be used
 * by the deployment.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class VirtualSecurityDomainNameProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        SecurityMetaData securityMetaData = deploymentUnit.getAttachment(ATTACHMENT_KEY);
        // Only act on the deployment unit that was itself marked as requiring a virtual domain,
        // never on the root-level flag: sub-deployments of an EAR run this phase concurrently, so
        // consulting the root flag here would race with the marking sub-deployment's thread and
        // could leak the virtual domain into siblings (e.g. EJB JARs), overriding their own
        // @SecurityDomain configuration. The root deployment's SecurityMetaData is instead set
        // by the marking unit via setTopLevelDeploymentSecurityMetaData.
        if (securityMetaData != null && isVirtualDomainRequiredByUnit(deploymentUnit)) {
            ServiceName virtualDomainName = virtualDomainName(deploymentUnit);
            securityMetaData.setSecurityDomain(virtualDomainName);
            setTopLevelDeploymentSecurityMetaData(deploymentUnit, virtualDomainName);
        }
    }

}
