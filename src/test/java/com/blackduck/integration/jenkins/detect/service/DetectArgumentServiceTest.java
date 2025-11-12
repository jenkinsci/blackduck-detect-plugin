package com.blackduck.integration.jenkins.detect.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;
import com.blackduck.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.blackduck.integration.log.LogLevel;
import com.blackduck.integration.util.IntEnvironmentVariables;

import hudson.model.TaskListener;

class DetectArgumentServiceTest {
    private final JenkinsVersionHelper jenkinsVersionHelper = mock(JenkinsVersionHelper.class);

    private static final String ERROR_MESSAGE = "Output Detect Command does not contain: ";
    private static final String LOGGING_LEVEL_KEY = "--logging.level.detect";
    private static final String EXPECTED_TEST_INVOCATION_PARAMETER = "TestInvocationParameter";
    private static final String EXPECTED_JENKINS_VERSION = "JenkinsVersion";
    private static final String PLUGIN_NAME = "blackduck-detect";
    private static final String EXPECTED_JENKINS_PLUGIN_VERSION = "JenkinsPluginVersion";
    private static final String JENKINS_VERSION_PARAM = "--detect.phone.home.passthrough.jenkins.version";
    private static final String PLUGIN_VERSION_PARAM = "--detect.phone.home.passthrough.jenkins.plugin.version";

    private final Function<String, String> strategyEscaper = Function.identity();
    private final List<String> invocationParameters = Collections.singletonList(EXPECTED_TEST_INVOCATION_PARAMETER);

    private IntEnvironmentVariables intEnvironmentVariables;
    private ByteArrayOutputStream byteArrayOutputStream;

    private DetectArgumentService detectArgumentService;
    private Map<String, String> inputDetectProperties;
    private Map<String, String> expectedArgsFromEnvironment;
    private Map<String, String> expectedArgsFromPlugin;

    @BeforeEach
    void setup() {
        // Setup logger
        TaskListener taskListener = mock(TaskListener.class);
        JenkinsIntLogger jenkinsIntLogger = JenkinsIntLogger.logToListener(taskListener);

        jenkinsIntLogger.setLogLevel(LogLevel.DEBUG);
        intEnvironmentVariables = IntEnvironmentVariables.includeSystemEnv();
        intEnvironmentVariables.put("DETECT_PLUGIN_ESCAPING", "true");

        // Set default expected values. Tests will apply different if needed
        byteArrayOutputStream = new ByteArrayOutputStream();
        when(taskListener.getLogger()).thenReturn(new PrintStream(byteArrayOutputStream));
        when(jenkinsVersionHelper.getJenkinsVersion()).thenReturn(Optional.of(EXPECTED_JENKINS_VERSION));
        when(jenkinsVersionHelper.getPluginVersion(PLUGIN_NAME)).thenReturn(Optional.of(EXPECTED_JENKINS_PLUGIN_VERSION));

        // These are the standard input detect properties
        inputDetectProperties = new LinkedHashMap<>();
        inputDetectProperties.put("--detect.docker.passthrough.service.timeout", "1234567890");
        inputDetectProperties.put("--detect.cleanup", "ThisIsFalse");

        // These are the standard expected detect properties. Tests will update if needed.
        expectedArgsFromEnvironment = new LinkedHashMap<>();
        expectedArgsFromEnvironment.putAll(inputDetectProperties);

        // These are the automatically appended detect properties added by DetectArgumentService(). Tests will update if needed.
        expectedArgsFromPlugin = new LinkedHashMap<>();
        expectedArgsFromPlugin.put(JENKINS_VERSION_PARAM, EXPECTED_JENKINS_VERSION);
        expectedArgsFromPlugin.put(PLUGIN_VERSION_PARAM, EXPECTED_JENKINS_PLUGIN_VERSION);
        expectedArgsFromPlugin.put(LOGGING_LEVEL_KEY, jenkinsIntLogger.getLogLevel().toString());

        // Create object in test
        detectArgumentService = new DetectArgumentService(jenkinsIntLogger, jenkinsVersionHelper);
    }

    private String createDetectPropertiesInputString() {
        return inputDetectProperties.keySet().stream()
            .map(key -> key + "=" + inputDetectProperties.get(key))
            .collect(Collectors.joining(" "));
    }

    @Test
    void testGetDetectArguments() {
        List<String> detectCommandLine = detectArgumentService.getDetectArguments(
            intEnvironmentVariables,
            strategyEscaper,
            invocationParameters,
            createDetectPropertiesInputString()
        );

        assertEquals(6, detectCommandLine.size()); // Invocation args (1) + passed args (2) + auto added args (3)
        assertEquals(0, byteArrayOutputStream.size(), "Output log should be empty");
        commonValidation(detectCommandLine, expectedArgsFromEnvironment, expectedArgsFromPlugin);
    }

    @Test
    void testShouldEscapeFalse() {
        intEnvironmentVariables.put("DETECT_PLUGIN_ESCAPING", "false");

        List<String> detectCommandLine = detectArgumentService.getDetectArguments(
            intEnvironmentVariables,
            strategyEscaper,
            invocationParameters,
            createDetectPropertiesInputString()
        );

        assertEquals(6, detectCommandLine.size()); // Invocation args (1) + passed args (2) + auto added args (3)
        assertEquals(0, byteArrayOutputStream.size(), "Output log should be empty");
        commonValidation(detectCommandLine, expectedArgsFromEnvironment, expectedArgsFromPlugin);
    }

    @Test
    void testVariableReplaced() {
        // Set up intEnvironmentVariables to only contain custom variable to verify search and replace of the same
        IntEnvironmentVariables intEnvironmentVariables = IntEnvironmentVariables.empty();
        intEnvironmentVariables.put("TRUST_CERT", "false");
        inputDetectProperties.put("--blackduck.trust.cert", "$TRUST_CERT");
        expectedArgsFromEnvironment.put("--blackduck.trust.cert", "false");

        List<String> detectCommandLine = detectArgumentService.getDetectArguments(
            intEnvironmentVariables,
            strategyEscaper,
            invocationParameters,
            createDetectPropertiesInputString()
        );

        assertEquals(7, detectCommandLine.size()); // Invocation args (1) + passed args (3) + auto added args (3)
        assertEquals(0, byteArrayOutputStream.size(), "Output log should be empty");
        commonValidation(detectCommandLine, expectedArgsFromEnvironment, expectedArgsFromPlugin);
    }

    @Test
    void testVariableNotReplaced() {
        inputDetectProperties.put("$VISIBLE", "visible");

        List<String> detectCommandLine = detectArgumentService.getDetectArguments(
            intEnvironmentVariables,
            strategyEscaper,
            invocationParameters,
            createDetectPropertiesInputString()
        );

        assertEquals(7, detectCommandLine.size()); // Invocation args (1) + passed args (3) + auto added args (3)
        assertTrue(
            byteArrayOutputStream.toString().contains("A variable may not have been properly replaced in resolved argument: $VISIBLE"),
            "Log should contain message about unable to replace variable."
        );
        commonValidation(detectCommandLine, expectedArgsFromEnvironment, expectedArgsFromPlugin);
    }

    @Test
    void testCustomLogLevel() {
        String expectedCustomLogLevel = "TestLogLevel";
        inputDetectProperties.put(LOGGING_LEVEL_KEY, expectedCustomLogLevel);
        expectedArgsFromPlugin.replace(LOGGING_LEVEL_KEY, expectedCustomLogLevel);

        List<String> detectCommandLine = detectArgumentService.getDetectArguments(
            intEnvironmentVariables,
            strategyEscaper,
            invocationParameters,
            createDetectPropertiesInputString()
        );

        assertEquals(6, detectCommandLine.size()); // Invocation args (1) + passed args (3) + auto added args (2)
        assertEquals(0, byteArrayOutputStream.size(), "Output log should be empty");
        commonValidation(detectCommandLine, expectedArgsFromEnvironment, expectedArgsFromPlugin);
    }

    @Test
    void testNoDetectArguments() {
        List<String> detectCommandLine = detectArgumentService.getDetectArguments(intEnvironmentVariables, strategyEscaper, invocationParameters, "\t");

        assertEquals(4, detectCommandLine.size()); // Invocation args (1) + passed args (0) + auto added args (3)
        assertEquals(0, byteArrayOutputStream.size(), "Output log should be empty");
        commonValidation(detectCommandLine, new LinkedHashMap<>(), expectedArgsFromPlugin);
    }

    @Test
    void testUnknownJenkinsVersion() {
        when(jenkinsVersionHelper.getJenkinsVersion()).thenReturn(Optional.empty());
        when(jenkinsVersionHelper.getPluginVersion(PLUGIN_NAME)).thenReturn(Optional.empty());
        expectedArgsFromPlugin.replace(JENKINS_VERSION_PARAM, EXPECTED_JENKINS_VERSION, "<unknown>");
        expectedArgsFromPlugin.replace(PLUGIN_VERSION_PARAM, EXPECTED_JENKINS_PLUGIN_VERSION, "<unknown>");

        List<String> detectCommandLine = detectArgumentService.getDetectArguments(
            intEnvironmentVariables,
            strategyEscaper,
            invocationParameters,
            createDetectPropertiesInputString()
        );

        assertEquals(6, detectCommandLine.size()); // Invocation args (1) + passed args (2) + auto added args (3)
        assertEquals(0, byteArrayOutputStream.size(), "Output log should be empty");
        commonValidation(detectCommandLine, expectedArgsFromEnvironment, expectedArgsFromPlugin);
    }

    @Test
    void testEmptyIntEnvironmentVariables() {
        IntEnvironmentVariables intEnvironmentVariables = IntEnvironmentVariables.empty();

        List<String> detectCommandLine = detectArgumentService.getDetectArguments(
            intEnvironmentVariables,
            strategyEscaper,
            invocationParameters,
            createDetectPropertiesInputString()
        );

        assertEquals(6, detectCommandLine.size()); // Invocation args (1) + passed args (2) + auto added args (3)
        assertEquals(0, byteArrayOutputStream.size(), "Output log should be empty");
        commonValidation(detectCommandLine, expectedArgsFromEnvironment, expectedArgsFromPlugin);
    }

    private void commonValidation(List<String> detectCommandLine, Map<String, String> expectedPropertiesFromEnvironment, Map<String, String> expectedPropertiesFromPlugin) {
        assertEquals(EXPECTED_TEST_INVOCATION_PARAMETER, detectCommandLine.get(0));

        // Validate that detectCommandLine DOES contain all the expected properties
        validateMapProperties(expectedPropertiesFromEnvironment, detectCommandLine);
        validateMapProperties(expectedPropertiesFromPlugin, detectCommandLine);
    }

    private void validateMapProperties(Map<String, String> detectProperties, List<String> detectCommandLine) {
        detectProperties.forEach((key, value) -> {
            assertTrue(detectCommandLine.stream().anyMatch(argument -> argument.contains(key)), ERROR_MESSAGE + key);
            assertTrue(detectCommandLine.stream().anyMatch(argument -> argument.contains("=" + value)), ERROR_MESSAGE + "=" + value);
        });
    }

}
