/*
 * blackduck-detect
 *
 * Copyright (c) 2024 Black Duck Software, Inc.
 *
 * Use subject to the terms and conditions of the Black Duck Software End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.blackduck.integration.jenkins.detect.service.strategy;

import com.blackduck.integration.IntegrationEscapeUtils;
import com.blackduck.integration.jenkins.detect.exception.DetectJenkinsException;
import com.google.gson.Gson;
import com.blackduck.integration.exception.IntegrationException;
import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;
import com.blackduck.integration.jenkins.wrapper.JenkinsProxyHelper;
import com.blackduck.integration.rest.HttpUrl;
import com.blackduck.integration.rest.client.IntHttpClient;
import com.blackduck.integration.rest.credentials.Credentials;
import com.blackduck.integration.rest.credentials.CredentialsBuilder;
import com.blackduck.integration.rest.proxy.ProxyInfo;
import com.blackduck.integration.rest.proxy.ProxyInfoBuilder;
import com.blackduck.integration.rest.request.Request;
import com.blackduck.integration.rest.response.Response;
import com.blackduck.integration.util.OperatingSystemType;
import jenkins.security.MasterToSlaveCallable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;

public class DetectScriptStrategy extends DetectExecutionStrategy {
    public static final String DETECT_INSTALL_DIRECTORY = "Detect_Installation";
    public static final String SUPPORTED_SHELL_SCRIPT_URL = "https://detect.blackduck.com/detect11.sh";
    public static final String SHELL_SCRIPT_FILENAME = "detect11.sh";
    public static final String SUPPORTED_POWERSHELL_SCRIPT_URL = "https://detect.blackduck.com/detect11.ps1";
    public static final String POWERSHELL_SCRIPT_FILENAME = "detect11.ps1";

    private final JenkinsIntLogger logger;
    private final OperatingSystemType operatingSystemType;
    private final JenkinsProxyHelper jenkinsProxyHelper;
    private final String toolsDirectory;

    public DetectScriptStrategy(JenkinsIntLogger logger, JenkinsProxyHelper jenkinsProxyHelper, OperatingSystemType operatingSystemType, String toolsDirectory) {
        this.logger = logger;
        this.jenkinsProxyHelper = jenkinsProxyHelper;
        this.operatingSystemType = operatingSystemType;
        this.toolsDirectory = toolsDirectory;
    }

    @Override
    public Function<String, String> getArgumentEscaper() {
        if (operatingSystemType == OperatingSystemType.WINDOWS) {
            return IntegrationEscapeUtils::escapePowerShell;
        }
        return IntegrationEscapeUtils::escapeXSI;
    }

    @Override
    public MasterToSlaveCallable<ArrayList<String>, IntegrationException> getSetupCallable() throws IntegrationException {
        String scriptUrl;
        String scriptFileName;
        if (operatingSystemType == OperatingSystemType.WINDOWS) {
            scriptUrl = SUPPORTED_POWERSHELL_SCRIPT_URL;
            scriptFileName = POWERSHELL_SCRIPT_FILENAME;
        } else {
            scriptUrl = SUPPORTED_SHELL_SCRIPT_URL;
            scriptFileName = SHELL_SCRIPT_FILENAME;
        }

        // ProxyInfo itself isn't serializable, so we unpack it into serializable pieces and rebuild it later when we download the script. -- rotte JUL 2020
        ProxyInfo proxyInfo;
        try {
            proxyInfo = jenkinsProxyHelper.getProxyInfo(scriptUrl);
        } catch (IllegalArgumentException e) {
            logger.warn("Black Duck Detect for Jenkins could not resolve proxy info from Jenkins because: " + e.getMessage());
            logger.warn("Continuing without proxy...");
            logger.trace("Stack trace:", e);
            proxyInfo = ProxyInfo.NO_PROXY_INFO;
        }

        String proxyHost = proxyInfo.getHost().orElse(null);
        int proxyPort = proxyInfo.getPort();
        String proxyUsername = proxyInfo.getUsername().orElse(null);
        String proxyPassword = proxyInfo.getPassword().orElse(null);
        String proxyNtlmDomain = proxyInfo.getNtlmDomain().orElse(null);
        String proxyNtlmWorkstation = proxyInfo.getNtlmWorkstation().orElse(null);
        return new SetupCallableImpl(
            logger,
            toolsDirectory,
            scriptUrl,
            scriptFileName,
            proxyHost,
            proxyPort,
            proxyUsername,
            proxyPassword,
            proxyNtlmDomain,
            proxyNtlmWorkstation
        );
    }

    public static class SetupCallableImpl extends MasterToSlaveCallable<ArrayList<String>, IntegrationException> {
        private static final long serialVersionUID = -4954105356640324485L;
        private final JenkinsIntLogger logger;
        private final String toolsDirectory;
        private final String scriptUrl;
        private final String proxyHost;
        private final int proxyPort;
        private final String proxyUsername;
        private final String proxyPassword;
        private final String proxyNtlmDomain;
        private final String proxyNtlmWorkstation;
        private final String scriptFileName;

        public SetupCallableImpl(
            JenkinsIntLogger logger, String toolsDirectory, String scriptUrl, String scriptFileName, String proxyHost, int proxyPort, String proxyUsername, String proxyPassword,
            String proxyNtlmDomain, String proxyNtlmWorkstation
        ) {
            this.logger = logger;
            this.toolsDirectory = toolsDirectory;
            this.scriptUrl = scriptUrl;
            this.scriptFileName = scriptFileName;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.proxyUsername = proxyUsername;
            this.proxyPassword = proxyPassword;
            this.proxyNtlmDomain = proxyNtlmDomain;
            this.proxyNtlmWorkstation = proxyNtlmWorkstation;
        }

        @Override
        public ArrayList<String> call() throws IntegrationException {
            String scriptRemotePath;

            try {
                Path installationDirectory = Paths.get(toolsDirectory, DETECT_INSTALL_DIRECTORY);
                Files.createDirectories(installationDirectory);
                Path detectScriptPath = installationDirectory.resolve(scriptFileName);

                logger.info(String.format("Downloading Detect script from %s to %s", scriptUrl, detectScriptPath));

                IntHttpClient intHttpClient = new IntHttpClient(logger, new Gson(), 120, false, rebuildProxyInfo());
                Request request = new Request.Builder().url(new HttpUrl(scriptUrl)).build();

                try (Response response = intHttpClient.execute(request)) {
                    response.throwExceptionForError();
                    Files.copy(response.getContent(), detectScriptPath, StandardCopyOption.REPLACE_EXISTING);
                }

                scriptRemotePath = detectScriptPath.toRealPath().toString();
            } catch (Exception e) {
                throw new DetectJenkinsException("[ERROR] The Detect script was not downloaded successfully: " + e.getMessage(), e);
            }

            if (OperatingSystemType.determineFromSystem() == OperatingSystemType.WINDOWS) {
                return new ArrayList<>(Arrays.asList("powershell", String.format("\"Import-Module '%s'; detect\"", scriptRemotePath)));
            }
            return new ArrayList<>(Arrays.asList("bash", scriptRemotePath));
        }

        private ProxyInfo rebuildProxyInfo() {
            CredentialsBuilder credentialsBuilder = Credentials.newBuilder();
            credentialsBuilder.setUsernameAndPassword(proxyUsername, proxyPassword);
            Credentials proxyCredentials = credentialsBuilder.build();

            ProxyInfoBuilder proxyInfoBuilder = ProxyInfo.newBuilder();
            proxyInfoBuilder.setHost(proxyHost);
            proxyInfoBuilder.setPort(proxyPort);
            proxyInfoBuilder.setCredentials(proxyCredentials);
            proxyInfoBuilder.setNtlmDomain(proxyNtlmDomain);
            proxyInfoBuilder.setNtlmWorkstation(proxyNtlmWorkstation);

            return proxyInfoBuilder.build();
        }
    }

}
