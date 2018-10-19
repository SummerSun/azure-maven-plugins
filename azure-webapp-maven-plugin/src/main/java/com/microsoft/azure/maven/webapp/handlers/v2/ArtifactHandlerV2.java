/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.maven.webapp.handlers.v2;

import com.google.common.io.Files;
import com.microsoft.azure.maven.Utils;
import com.microsoft.azure.maven.artifacthandler.ArtifactHandler;
import com.microsoft.azure.maven.deploytarget.DeployTarget;
import com.microsoft.azure.maven.webapp.AbstractWebAppMojo;
import com.microsoft.azure.maven.webapp.configuration.Deployment;
import com.microsoft.azure.maven.webapp.handlers.ArtifactHandlerUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.zeroturnaround.zip.ZipUtil;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ArtifactHandlerV2 implements ArtifactHandler {
    private AbstractWebAppMojo mojo;
    private static final int DEFAULT_MAX_RETRY_TIMES = 3;
    private static final String LOCAL_SETTINGS_FILE = "local.settings.json";

    public ArtifactHandlerV2(final AbstractWebAppMojo mojo) {
        this.mojo = mojo;
    }

    @Override
    public void publish(DeployTarget target) throws MojoExecutionException, IOException {
        final Deployment deployment = mojo.getDeployment();
        final List<Resource> resources = deployment.getResources();
        if (resources == null || resources.size() < 1) {
            throw new MojoExecutionException("The element <resources> inside deployment has to be set to do deploy.");
        }
        final String tempFolderPrefix = "azure-webapp-maven-plugin";
        final MavenProject project = mojo.getProject();
        final MavenSession session = mojo.getSession();
        final MavenResourcesFiltering filtering = mojo.getMavenResourcesFiltering();

        for (final Resource resource : resources) {
            final String targetPath = resource.getTargetPath();
            final String tempStagingFolder = java.nio.file.Files.createTempDirectory(tempFolderPrefix).toString();
            Utils.copyResources(project, session, filtering, Arrays.asList(resource), tempStagingFolder);
            doPublish(target, tempStagingFolder, targetPath);
        }
    }

    public void doPublish(final DeployTarget target, final String stagingDirectoryPath,
                          final String targetPath) throws MojoExecutionException {
        final File stagingDirectory = new File(stagingDirectoryPath);
        final File[] files = stagingDirectory.listFiles();
        if (!stagingDirectory.exists() || !stagingDirectory.isDirectory() || files.length == 0) {
            throw new MojoExecutionException(
                String.format("Staging directory: '%s' is empty.", stagingDirectory.getAbsolutePath()));

        }
        for (final File file : files) {
            if ("war".equalsIgnoreCase(Files.getFileExtension(file.getName()))) {
                publishArtifactViaWarDeploy(target, file, targetPath);
                file.delete();
            }
        }
        publishArtifactViaZipDeploy(target, stagingDirectoryPath);
    }

    public void publishArtifactViaZipDeploy(final DeployTarget target,
                                            final String sourcePath) throws MojoExecutionException {
        final File sourceDirectory = new File(sourcePath);
        final File zipFile = new File(sourceDirectory + ".zip");
        ZipUtil.pack(sourceDirectory, zipFile);
        ZipUtil.removeEntry(zipFile, LOCAL_SETTINGS_FILE);

        // Add retry logic here to avoid Kudu's socket timeout issue.
        // More details: https://github.com/Microsoft/azure-maven-plugins/issues/339
        int retryCount = 0;
        while (retryCount < DEFAULT_MAX_RETRY_TIMES) {
            retryCount += 1;
            try {
                target.zipDeploy(zipFile);
                return;
            } catch (Exception e) {
                mojo.getLog().debug(
                    String.format("Exception occurred when deploying the zip package: %s, " +
                        "retrying immediately (%d/%d)", e.getMessage(), retryCount, DEFAULT_MAX_RETRY_TIMES));
            }
        }

        throw new MojoExecutionException(String.format("The zip deploy failed after %d times of retry.", retryCount));
    }

    public void publishArtifactViaWarDeploy(final DeployTarget target, final File war,
                                            final String contextPath) throws MojoExecutionException {
        final Runnable warDeployExecutor = ArtifactHandlerUtils.getRealWarDeployExecutor(target, war, contextPath);

        // Add retry logic here to avoid Kudu's socket timeout issue.
        // More details: https://github.com/Microsoft/azure-maven-plugins/issues/339
        int retryCount = 0;
        mojo.getLog().info(String.format("Deploying the war file: %s...", war.getName()));

        while (retryCount < DEFAULT_MAX_RETRY_TIMES) {
            retryCount++;
            try {
                warDeployExecutor.run();
                return;
            } catch (Exception e) {
                mojo.getLog().info(String.format("Exception occurred when deploying war file to server: %s, " +
                    "retrying immediately (%d/%d)", e.getMessage(), retryCount, DEFAULT_MAX_RETRY_TIMES));
            }
        }
        throw new MojoExecutionException(
            String.format("Failed to deploy the war file after %d times of retry.", retryCount));
    }
}
