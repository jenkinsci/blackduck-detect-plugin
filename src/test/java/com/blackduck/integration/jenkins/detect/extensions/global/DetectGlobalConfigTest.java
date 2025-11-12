package com.blackduck.integration.jenkins.detect.extensions.global;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.util.FormValidation;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class DetectGlobalConfigTest {

    @SuppressWarnings("unused")
    private JenkinsRule jenkinsRule;

    @BeforeEach
    void setup(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    @Test
    void testMissingCredentials() {
        DetectGlobalConfig detectGlobalConfig = new DetectGlobalConfig();
        FormValidation formValidation = detectGlobalConfig.doTestBlackDuckConnection("https://blackduck.domain.com", "123", "30", true);

        assertEquals(FormValidation.Kind.ERROR, formValidation.kind);
        assertTrue(formValidation.getMessage().contains("token"));
        assertTrue(formValidation.getMessage().contains("password"));
        System.out.printf("Message: %s\n", formValidation.getMessage());
    }
}
