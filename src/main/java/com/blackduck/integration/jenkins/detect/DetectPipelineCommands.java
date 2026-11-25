/*
 * blackduck-detect
 *
 * Copyright (c) 2024 Black Duck Software, Inc.
 *
 * Use subject to the terms and conditions of the Black Duck Software End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.blackduck.integration.jenkins.detect;

import com.blackduck.integration.jenkins.detect.exception.DetectJenkinsException;
import com.blackduck.integration.jenkins.detect.extensions.DetectDownloadStrategy;
import com.blackduck.integration.exception.IntegrationException;
import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;

import java.io.IOException;

public class DetectPipelineCommands {
    private final JenkinsIntLogger logger;
    private final DetectRunner detectRunner;

    public DetectPipelineCommands(DetectRunner detectRunner, JenkinsIntLogger logger) {
        this.detectRunner = detectRunner;
        this.logger = logger;
    }

    public int runDetect(boolean returnStatus, String detectArgumentString, DetectDownloadStrategy detectDownloadStrategy) throws IOException, IntegrationException, InterruptedException {
        int exitCode = detectRunner.runDetect(null, detectArgumentString, detectDownloadStrategy);
        if (exitCode > 0) {
            String errorMsg = "Detect failed with exit code " + exitCode;
            if (returnStatus) {
                logger.error(errorMsg);
            } else {
                throw new DetectJenkinsException(errorMsg);
            }
        }
        return exitCode;
    }
}
