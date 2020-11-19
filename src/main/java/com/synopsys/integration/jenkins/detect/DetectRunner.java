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

import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.detect.extensions.DetectDownloadStrategy;
import com.synopsys.integration.jenkins.detect.service.DetectEnvironmentService;
import com.synopsys.integration.jenkins.detect.service.strategy.DetectDownloadStrategyService;
import com.synopsys.integration.jenkins.detect.service.strategy.DetectExecutionStrategy;
import com.synopsys.integration.jenkins.detect.service.strategy.DetectExecutionStrategyFactory;
import com.synopsys.integration.jenkins.detect.service.strategy.DetectExecutionStrategyOptions;
import com.synopsys.integration.util.IntEnvironmentVariables;

public class DetectRunner {
    private final DetectEnvironmentService detectEnvironmentService;
    private final DetectDownloadStrategyService detectDownloadStrategyService;
    private final DetectExecutionStrategyFactory detectExecutionStrategyFactory;

    public DetectRunner(DetectEnvironmentService detectEnvironmentService, DetectDownloadStrategyService detectDownloadStrategyService, DetectExecutionStrategyFactory detectExecutionStrategyFactory) {
        this.detectEnvironmentService = detectEnvironmentService;
        this.detectDownloadStrategyService = detectDownloadStrategyService;
        this.detectExecutionStrategyFactory = detectExecutionStrategyFactory;
    }

    public int runDetect(String remoteJdkHome, String detectArgumentString, DetectDownloadStrategy initialDetectDownloadStrategy) throws IOException, InterruptedException, IntegrationException {
        IntEnvironmentVariables intEnvironmentVariables = detectEnvironmentService.createDetectEnvironment();
        //TODO we should merge the download strategy service and creation of strategy options
        DetectDownloadStrategy correctDownloadStrategy = detectDownloadStrategyService.determineCorrectDownloadStrategy(initialDetectDownloadStrategy);
        DetectExecutionStrategyOptions detectExecutionStrategyOptions = new DetectExecutionStrategyOptions(intEnvironmentVariables, correctDownloadStrategy);
        DetectExecutionStrategy detectExecutionStrategy = detectExecutionStrategyFactory.createDetectExecutionStrategy(detectExecutionStrategyOptions, remoteJdkHome);

        return detectExecutionStrategy.runStrategy(detectArgumentString);
    }
}
