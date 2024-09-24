/*
 * blackduck-detect
 *
 * Copyright (c) 2024 Black Duck Software, Inc.
 *
 * Use subject to the terms and conditions of the Black Duck Software End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.blackduck.integration.jenkins.detect;

import com.blackduck.integration.jenkins.detect.extensions.DetectDownloadStrategy;
import com.blackduck.integration.jenkins.service.JenkinsBuildService;

public class DetectFreestyleCommands {
    private final JenkinsBuildService jenkinsBuildService;
    private final DetectRunner detectRunner;

    public DetectFreestyleCommands(JenkinsBuildService jenkinsBuildService, DetectRunner detectRunner) {
        this.jenkinsBuildService = jenkinsBuildService;
        this.detectRunner = detectRunner;
    }

    public void runDetect(String detectArgumentString, DetectDownloadStrategy detectDownloadStrategy) {
        try {
            String remoteJdkHome = jenkinsBuildService.getJDKRemoteHomeOrEmpty().orElse(null);
            int exitCode = detectRunner.runDetect(remoteJdkHome, detectArgumentString, detectDownloadStrategy);
            if (exitCode > 0) {
                jenkinsBuildService.markBuildFailed("Detect failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jenkinsBuildService.markBuildInterrupted();
        } catch (Exception e) {
            jenkinsBuildService.markBuildFailed(e);
        }
    }

}
