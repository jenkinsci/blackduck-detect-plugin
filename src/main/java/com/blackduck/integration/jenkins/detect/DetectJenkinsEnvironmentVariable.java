/*
 * blackduck-detect
 *
 * Copyright (c) 2024 Black Duck Software, Inc.
 *
 * Use subject to the terms and conditions of the Black Duck Software End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.blackduck.integration.jenkins.detect;

public enum DetectJenkinsEnvironmentVariable {
    USER_PROVIDED_JAR_PATH("DETECT_JAR"),
    SHOULD_ESCAPE("DETECT_PLUGIN_ESCAPING");

    private final String environmentVariable;

    DetectJenkinsEnvironmentVariable(String environmentVariable) {
        this.environmentVariable = environmentVariable;
    }

    public String stringValue() {
        return environmentVariable;
    }
}
