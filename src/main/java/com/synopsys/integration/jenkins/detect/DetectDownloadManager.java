/**
 * blackduck-detect
 *
 * Copyright (c) 2020 Synopsys, Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.jenkins.detect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.JenkinsProxyHelper;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.rest.client.IntHttpClient;
import com.synopsys.integration.rest.request.Request;
import com.synopsys.integration.rest.request.Response;
import com.synopsys.integration.rest.proxy.ProxyInfo;

public class DetectDownloadManager {
    public static final String DETECT_INSTALL_DIRECTORY = "Detect_Installation";
    public static final String LATEST_SHELL_SCRIPT_URL = "https://detect.synopsys.com/detect.sh";
    public static final String LATEST_POWERSHELL_SCRIPT_URL = "https://detect.synopsys.com/detect.ps1";

    private final IntLogger logger;
    private final String toolsDirectory;

    public DetectDownloadManager(final IntLogger logger, final String toolsDirectory) {
        this.logger = logger;
        this.toolsDirectory = toolsDirectory;
    }

    public Path downloadScript(final String scriptDownloadUrl) throws IntegrationException, IOException {
    	return downloadScript(scriptDownloadUrl, null);
    }

    public Path downloadScript(final String scriptDownloadUrl, final ProxyInfo proxyInfo) throws IntegrationException, IOException {
        final String scriptFileName = scriptDownloadUrl.substring(scriptDownloadUrl.lastIndexOf("/") + 1).trim();
        final Path scriptDownloadDirectory = prepareScriptDownloadDirectory();
        final Path localScriptFile = scriptDownloadDirectory.resolve(scriptFileName);

        if (shouldDownloadScript(scriptDownloadUrl, localScriptFile)) {
            logger.info("Downloading Detect script from " + scriptDownloadUrl + " to " + localScriptFile);
            downloadScriptTo(scriptDownloadUrl, localScriptFile, Optional.ofNullable(proxyInfo));
        } else {
            logger.info("Running already installed Detect script " + localScriptFile);
        }

        return localScriptFile;
    }

    private boolean shouldDownloadScript(final String scriptDownloadUrl, final Path localScriptFile) {
        return Files.notExists(localScriptFile) && StringUtils.isNotBlank(scriptDownloadUrl);
    }

    private Path prepareScriptDownloadDirectory() throws IntegrationException {
        final Path installationDirectory = Paths.get(toolsDirectory, DETECT_INSTALL_DIRECTORY);

        try {
            Files.createDirectories(installationDirectory);
        } catch (final Exception e) {
            throw new IntegrationException("Could not create the Detect installation directory: " + installationDirectory, e);
        }

        return installationDirectory;
    }

    private void downloadScriptTo(final String url, final Path path, final Optional<ProxyInfo> proxyInfo) throws IntegrationException, IOException {
        final IntHttpClient intHttpClient = new IntHttpClient(logger, 120, true, proxyInfo.orElse(JenkinsProxyHelper.getProxyInfoFromJenkins(url)));

        final Request request = new Request.Builder().uri(url).build();
        try (final Response response = intHttpClient.execute(request)) {
            Files.copy(response.getContent(), path);
        }
    }

}
