/*
 * blackduck-detect
 *
 * Copyright (c) 2024 Black Duck Software, Inc.
 *
 * Use subject to the terms and conditions of the Black Duck Software End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.blackduck.integration.jenkins.detect.service;

import com.blackduck.integration.jenkins.detect.DetectJenkinsEnvironmentVariable;
import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;
import com.blackduck.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.blackduck.integration.phonehome.request.PhoneHomeRequestBody;
import com.blackduck.integration.util.IntEnvironmentVariables;
import hudson.Util;
import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.types.Commandline;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DetectArgumentService {
    private static final String DETECT_LOGLEVEL_ARGUMENT = "logging.level.detect";
    private static final String DETECT_PHONEHOME_JENKINS_VERSION_ARGUMENT = "detect.phone.home.passthrough.jenkins.version";
    private static final String DETECT_PHONEHOME_PLUGIN_VERSION_ARGUMENT = "detect.phone.home.passthrough.jenkins.plugin.version";
    private final JenkinsIntLogger logger;
    private final JenkinsVersionHelper jenkinsVersionHelper;

    public DetectArgumentService(JenkinsIntLogger logger, JenkinsVersionHelper jenkinsVersionHelper) {
        this.logger = logger;
        this.jenkinsVersionHelper = jenkinsVersionHelper;
    }

    public List<String> getDetectArguments(
        IntEnvironmentVariables intEnvironmentVariables,
        Function<String, String> strategyEscaper,
        List<String> initialArguments,
        String detectArgumentString
    ) {
        boolean shouldEscape = Boolean.parseBoolean(intEnvironmentVariables.getValue(DetectJenkinsEnvironmentVariable.SHOULD_ESCAPE.stringValue(), "true"));
        Function<String, String> argumentEscaper;
        if (shouldEscape) {
            argumentEscaper = strategyEscaper;
        } else {
            argumentEscaper = Function.identity();
        }

        List<String> detectArguments = new ArrayList<>();
        detectArguments.addAll(initialArguments);
        detectArguments.addAll(parseDetectArgumentString(intEnvironmentVariables.getVariables(), argumentEscaper, detectArgumentString));

        if (detectArguments.stream().noneMatch(argument -> argument.contains(DETECT_LOGLEVEL_ARGUMENT))) {
            detectArguments.add(asEscapedDetectArgument(argumentEscaper, DETECT_LOGLEVEL_ARGUMENT, logger.getLogLevel().toString()));
        }

        String jenkinsVersion = jenkinsVersionHelper.getJenkinsVersion().orElse(PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE);
        String pluginVersion = jenkinsVersionHelper.getPluginVersion("blackduck-detect").orElse(PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE);

        detectArguments.add(asEscapedDetectArgument(argumentEscaper, DETECT_PHONEHOME_JENKINS_VERSION_ARGUMENT, jenkinsVersion));
        detectArguments.add(asEscapedDetectArgument(argumentEscaper, DETECT_PHONEHOME_PLUGIN_VERSION_ARGUMENT, pluginVersion));

        return detectArguments;
    }

    private String asEscapedDetectArgument(Function<String, String> escaper, String key, String rawValue) {
        return String.format("--%s=%s", key, escaper.apply(rawValue));
    }

    public List<String> parseDetectArgumentString(
        Map<String, String> environmentVariables, Function<String, String> argumentEscaper, String argumentString
    ) {
        return Arrays.stream(Commandline.translateCommandline(argumentString))
            .map(argumentBlobString -> argumentBlobString.split("\\r?\\n"))
            .flatMap(Arrays::stream)
            .map(argument -> Util.replaceMacro(argument, environmentVariables))
            .filter(this::validateExpandedArguments)
            .filter(Objects::nonNull)
            .map(argument -> escapeArgument(argument, argumentEscaper))
            .collect(Collectors.toList());
    }

    private boolean validateExpandedArguments(String argument) {
        if (StringUtils.isBlank(argument)) {
            return false;
        }

        if (argument.contains("$")) {
            logger.warn("A variable may not have been properly replaced in resolved argument: " + argument + ". Make sure the variable has been properly defined.");
        }

        return true;
    }

    private String escapeArgument(String argument, Function<String, String> escaper) {
        // Assume a cleaned argument, then test
        String cleanedArg = argument;
        if (argument.startsWith("--") && argument.contains("=") && !argument.contains("&")) {
            String[] splitArgument = argument.split("=", 2);
            //The api token should not be escaped... if it contains "=" or "==" padding, it would cause probs.
            String endArg = splitArgument[0].contains("blackduck.api.token") ? splitArgument[1] : escaper.apply(splitArgument[1]);
            cleanedArg = splitArgument[0] + "=" + endArg;
        }

        return cleanedArg;
    }
}
