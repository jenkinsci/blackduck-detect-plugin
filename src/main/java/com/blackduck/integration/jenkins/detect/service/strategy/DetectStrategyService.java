/*
 * blackduck-detect
 *
 * Copyright (c) 2024 Black Duck Software, Inc.
 *
 * Use subject to the terms and conditions of the Black Duck Software End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.blackduck.integration.jenkins.detect.service.strategy;

import com.blackduck.integration.jenkins.detect.extensions.AirGapDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.InheritFromGlobalDownloadStrategy;
import com.blackduck.integration.jenkins.detect.DetectJenkinsEnvironmentVariable;
import com.blackduck.integration.jenkins.detect.exception.DetectJenkinsException;
import com.blackduck.integration.jenkins.detect.extensions.DetectDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.global.DetectGlobalConfig;
import com.blackduck.integration.exception.IntegrationException;
import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;
import com.blackduck.integration.jenkins.service.JenkinsConfigService;
import com.blackduck.integration.jenkins.wrapper.JenkinsProxyHelper;
import com.blackduck.integration.util.IntEnvironmentVariables;
import com.blackduck.integration.util.OperatingSystemType;
import org.apache.commons.lang3.StringUtils;

public class DetectStrategyService {
    private final JenkinsIntLogger logger;
    private final String remoteTempWorkspacePath;
    private final JenkinsProxyHelper jenkinsProxyHelper;
    private final JenkinsConfigService jenkinsConfigService;

    public DetectStrategyService(JenkinsIntLogger logger, JenkinsProxyHelper jenkinsProxyHelper, String remoteTempWorkspacePath, JenkinsConfigService jenkinsConfigService) {
        this.logger = logger;
        this.jenkinsProxyHelper = jenkinsProxyHelper;
        this.remoteTempWorkspacePath = remoteTempWorkspacePath;
        this.jenkinsConfigService = jenkinsConfigService;
    }

    public DetectExecutionStrategy getExecutionStrategy(
        IntEnvironmentVariables intEnvironmentVariables,
        OperatingSystemType operatingSystemType,
        String remoteJdkHome,
        DetectDownloadStrategy detectDownloadStrategy
    )
        throws IntegrationException {
        String loggingMessage = "Running Detect using configured strategy: ";

        if (detectDownloadStrategy == null || detectDownloadStrategy instanceof InheritFromGlobalDownloadStrategy) {
            DetectGlobalConfig detectGlobalConfig = jenkinsConfigService.getGlobalConfiguration(DetectGlobalConfig.class)
                .orElseThrow(() -> new DetectJenkinsException("Could not find Detect configuration. Check Jenkins System Configuration to ensure Detect is configured correctly."));
            detectDownloadStrategy = detectGlobalConfig.getDownloadStrategy();

            if (detectDownloadStrategy == null) {
                detectDownloadStrategy = detectGlobalConfig.getDefaultDownloadStrategy();
                loggingMessage = "System configured strategy not found, running Detect using default configured system strategy: ";
            } else {
                loggingMessage = "Running Detect using configured system strategy: ";
            }
        }

        logger.info(loggingMessage + detectDownloadStrategy.getDisplayName());

        String detectJarPath = intEnvironmentVariables.getValue(DetectJenkinsEnvironmentVariable.USER_PROVIDED_JAR_PATH.stringValue());
        DetectExecutionStrategy detectExecutionStrategy;

        if (detectDownloadStrategy instanceof AirGapDownloadStrategy) {
            detectExecutionStrategy = new DetectAirGapJarStrategy(
                logger,
                intEnvironmentVariables,
                remoteJdkHome,
                jenkinsConfigService,
                (AirGapDownloadStrategy) detectDownloadStrategy
            );
        } else if (StringUtils.isNotBlank(detectJarPath)) {
            detectExecutionStrategy = new DetectJarStrategy(logger, intEnvironmentVariables, remoteJdkHome, detectJarPath);
        } else {
            detectExecutionStrategy = new DetectScriptStrategy(logger, jenkinsProxyHelper, operatingSystemType, remoteTempWorkspacePath);
        }

        return detectExecutionStrategy;
    }

}
