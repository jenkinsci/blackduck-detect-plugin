/*
 * blackduck-detect
 *
 * Copyright (c) 2024 Black Duck Software, Inc.
 *
 * Use subject to the terms and conditions of the Black Duck Software End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.blackduck.integration.jenkins.detect.service.strategy;

import com.blackduck.integration.exception.IntegrationException;
import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;
import com.blackduck.integration.util.IntEnvironmentVariables;
import jenkins.security.MasterToSlaveCallable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

public class DetectJarStrategy extends DetectExecutionStrategy {
    private final JenkinsIntLogger logger;
    private final String detectJarPath;
    private final IntEnvironmentVariables intEnvironmentVariables;
    private final String remoteJdkHome;

    public DetectJarStrategy(JenkinsIntLogger logger, IntEnvironmentVariables intEnvironmentVariables, String remoteJdkHome, String detectJarPath) {
        this.logger = logger;
        this.intEnvironmentVariables = intEnvironmentVariables;
        this.remoteJdkHome = remoteJdkHome;
        this.detectJarPath = detectJarPath;
    }

    @Override
    public Function<String, String> getArgumentEscaper() {
        return Function.identity();
    }

    @Override
    public MasterToSlaveCallable<ArrayList<String>, IntegrationException> getSetupCallable() {
        return new SetupCallableImpl(logger, intEnvironmentVariables.getVariables(), detectJarPath, remoteJdkHome);
    }

    public static class SetupCallableImpl extends MasterToSlaveCallable<ArrayList<String>, IntegrationException> {
        private static final long serialVersionUID = -8326836838838706367L;
        private final JenkinsIntLogger logger;
        private final Map<String, String> environmentVariables;
        private final String detectJarPath;
        private final String remoteJdkHome;

        public SetupCallableImpl(JenkinsIntLogger logger, Map<String, String> environmentVariables, String detectJarPath, String remoteJdkHome) {
            this.logger = logger;
            this.environmentVariables = environmentVariables;
            this.detectJarPath = detectJarPath;
            this.remoteJdkHome = remoteJdkHome;
        }

        @Override
        public ArrayList<String> call() {
            RemoteJavaService remoteJavaService = new RemoteJavaService(logger, remoteJdkHome, environmentVariables);
            String javaExecutablePath = remoteJavaService.getJavaExecutablePath();

            logger.info("Detect jar configured: " + detectJarPath);

            return new ArrayList<>(Arrays.asList(javaExecutablePath, "-jar", detectJarPath));
        }
    }

}
