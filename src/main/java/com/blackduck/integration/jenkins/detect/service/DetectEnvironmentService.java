/*
 * blackduck-detect
 *
 * Copyright (c) 2024 Black Duck Software, Inc.
 *
 * Use subject to the terms and conditions of the Black Duck Software End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.blackduck.integration.jenkins.detect.service;

import com.blackduck.integration.jenkins.detect.extensions.global.DetectGlobalConfig;
import com.blackduck.integration.blackduck.configuration.BlackDuckServerConfigBuilder;
import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;
import com.blackduck.integration.jenkins.service.JenkinsConfigService;
import com.blackduck.integration.jenkins.wrapper.BlackduckCredentialsHelper;
import com.blackduck.integration.jenkins.wrapper.JenkinsProxyHelper;
import com.blackduck.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.blackduck.integration.jenkins.wrapper.BlackduckCredentialsHelper;
import com.blackduck.integration.util.IntEnvironmentVariables;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public class DetectEnvironmentService {
    public static final String TIMEOUT = "DETECT_TIMEOUT";

    private final JenkinsIntLogger logger;
    private final JenkinsProxyHelper jenkinsProxyHelper;
    private final JenkinsVersionHelper jenkinsVersionHelper;
    private final BlackduckCredentialsHelper blackduckCredentialsHelper;
    private final Map<String, String> environmentVariables;
    private final JenkinsConfigService jenkinsConfigService;

    public DetectEnvironmentService(
        JenkinsIntLogger logger,
        JenkinsProxyHelper jenkinsProxyHelper,
        JenkinsVersionHelper jenkinsVersionHelper,
        BlackduckCredentialsHelper blackduckCredentialsHelper,
        JenkinsConfigService jenkinsConfigService,
        Map<String, String> environmentVariables
    ) {
        this.logger = logger;
        this.jenkinsProxyHelper = jenkinsProxyHelper;
        this.jenkinsVersionHelper = jenkinsVersionHelper;
        this.jenkinsConfigService = jenkinsConfigService;
        this.blackduckCredentialsHelper = blackduckCredentialsHelper;
        this.environmentVariables = environmentVariables;
    }

    public IntEnvironmentVariables createDetectEnvironment() {
        IntEnvironmentVariables intEnvironmentVariables = IntEnvironmentVariables.empty();
        intEnvironmentVariables.putAll(environmentVariables);
        logger.setLogLevel(intEnvironmentVariables);

        populateAllBlackDuckEnvironmentVariables(intEnvironmentVariables::put);

        Optional<String> pluginVersion = jenkinsVersionHelper.getPluginVersion("blackduck-detect");
        if (pluginVersion.isPresent()) {
            logger.info("Running Black Duck Detect Jenkins Plugin version: " + pluginVersion.get());
        } else {
            logger.info("Running Black Duck Detect Jenkins Plugin");
        }

        return intEnvironmentVariables;
    }

    private void populateAllBlackDuckEnvironmentVariables(BiConsumer<String, String> environmentPutter) {
        Optional<DetectGlobalConfig> detectGlobalConfig = jenkinsConfigService.getGlobalConfiguration(DetectGlobalConfig.class);
        if (!detectGlobalConfig.isPresent()) {
            return;
        }

        BlackDuckServerConfigBuilder blackDuckServerConfigBuilder = detectGlobalConfig.get().getBlackDuckServerConfigBuilder(jenkinsProxyHelper, blackduckCredentialsHelper);

        blackDuckServerConfigBuilder.getProperties()
            .forEach((builderPropertyKey, propertyValue) -> updateAndFilterVariables(environmentPutter, builderPropertyKey.getKey(), propertyValue));
    }

    private void updateAndFilterVariables(BiConsumer<String, String> environmentPutter, String key, String value) {
        String filteredKey = BlackDuckServerConfigBuilder.TIMEOUT_KEY.getKey().equals(key) ? TIMEOUT : key;

        if (StringUtils.isNoneBlank(filteredKey, value)) {
            environmentPutter.accept(filteredKey, value);
        }
    }

}
