/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.maven.webapp.handlers;

import com.microsoft.azure.maven.deploytarget.DeployTarget;
import com.microsoft.azure.maven.webapp.deploytarget.DeploymentSlotDeployTarget;
import com.microsoft.azure.maven.webapp.deploytarget.WebAppDeployTarget;
import org.apache.maven.plugin.MojoExecutionException;
import java.io.File;

public class ArtifactHandlerUtils {

    /**
     * Interfaces WebApp && DeploymentSlot define their own warDeploy API separately.
     * Ideally, it should be defined in their base interface WebAppBase.
     * {@link com.microsoft.azure.management.appservice.WebAppBase}
     * Comparing to abstracting an adapter for WebApp && DeploymentSlot, we choose a lighter solution:
     * work around to get the real implementation of warDeploy.
     */
    public static Runnable getRealWarDeployExecutor(final DeployTarget target, final File war,
                                                    final String path) throws MojoExecutionException {
        if (target instanceof WebAppDeployTarget) {
            return new Runnable() {
                @Override
                public void run() {
                    ((WebAppDeployTarget) target).warDeploy(war, path);
                }
            };
        }

        if (target instanceof DeploymentSlotDeployTarget) {
            return new Runnable() {
                @Override
                public void run() {
                    ((DeploymentSlotDeployTarget) target).warDeploy(war, path);
                }
            };
        }

        throw new MojoExecutionException("The type of deploy target is unknown," +
            " supported types are WebApp and DeploymentSlot.");
    }
}
