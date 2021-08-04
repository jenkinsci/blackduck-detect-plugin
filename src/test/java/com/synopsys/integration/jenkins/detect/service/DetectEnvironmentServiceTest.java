package com.synopsys.integration.jenkins.detect.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.synopsys.integration.blackduck.configuration.BlackDuckServerConfig;
import com.synopsys.integration.blackduck.configuration.BlackDuckServerConfigBuilder;
import com.synopsys.integration.jenkins.detect.extensions.global.DetectGlobalConfig;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.service.JenkinsConfigService;
import com.synopsys.integration.jenkins.wrapper.JenkinsProxyHelper;
import com.synopsys.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.synopsys.integration.jenkins.wrapper.JenkinsWrapper;
import com.synopsys.integration.jenkins.wrapper.SynopsysCredentialsHelper;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.model.TaskListener;

public class DetectEnvironmentServiceTest {
    private final TaskListener taskListenerMock = Mockito.mock(TaskListener.class);
    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private final JenkinsIntLogger jenkinsIntLogger = new JenkinsIntLogger(taskListenerMock);

    public final BlackDuckServerConfigBuilder blackDuckServerConfigBuilder = BlackDuckServerConfig.newBuilder().setTimeoutInSeconds(120);

    private final String junitKey = "__JUNIT_KEY__";
    private final String junitValue = "__JUNIT_VALUE__";

    private final JenkinsWrapper jenkinsWrapper = JenkinsWrapper.initializeFromJenkinsJVM();
    private final SynopsysCredentialsHelper synopsysCredentialsHelper = new SynopsysCredentialsHelper(jenkinsWrapper);
    private final JenkinsProxyHelper jenkinsProxyHelper = new JenkinsProxyHelper();

    private final DetectGlobalConfig detectGlobalConfig = Mockito.mock(DetectGlobalConfig.class);
    private final JenkinsConfigService jenkinsConfigServiceMock = Mockito.mock(JenkinsConfigService.class);
    private final JenkinsVersionHelper jenkinsVersionHelperMock = Mockito.mock(JenkinsVersionHelper.class);

    private DetectEnvironmentService detectEnvironmentService;

    @BeforeEach
    public void setUp() {
        Mockito.when(taskListenerMock.getLogger()).thenReturn(new PrintStream(byteArrayOutputStream));
        Mockito.when(jenkinsConfigServiceMock.getGlobalConfiguration(DetectGlobalConfig.class)).thenReturn(Optional.of(detectGlobalConfig));
        Mockito.when(detectGlobalConfig.getBlackDuckServerConfigBuilder(jenkinsProxyHelper, synopsysCredentialsHelper)).thenReturn(blackDuckServerConfigBuilder);

        detectEnvironmentService = new DetectEnvironmentService(jenkinsIntLogger, jenkinsProxyHelper, jenkinsVersionHelperMock, synopsysCredentialsHelper, jenkinsConfigServiceMock, new HashMap<>());
    }

    @Test
    public void testNoPluginVersionInLog() {
        detectEnvironmentService.createDetectEnvironment();
        assertTrue(byteArrayOutputStream.toString().contains("Running Synopsys Detect for Jenkins"), "Log should contain default message");
    }

    @Test
    public void testPluginVersionInLog() {
        String expectedJenkinsPluginVersion = "JenkinsPluginVersion";
        Mockito.when(jenkinsVersionHelperMock.getPluginVersion("blackduck-detect")).thenReturn(Optional.of(expectedJenkinsPluginVersion));

        detectEnvironmentService.createDetectEnvironment();
        assertTrue(byteArrayOutputStream.toString().contains(String.format("Running Synopsys Detect for Jenkins version: %s", expectedJenkinsPluginVersion)),
            "Log should contain message with plugin version");
    }

    @Test
    public void testNullsInDetectGlobalConfig() {
        blackDuckServerConfigBuilder.setProperty(junitKey, null);
        blackDuckServerConfigBuilder.setProperty(null, junitValue);
        blackDuckServerConfigBuilder.setTimeoutInSeconds(null);
        assertTrue(blackDuckServerConfigBuilder.getEnvironmentVariableKeys().contains(junitKey), String.format("Should contain key %s", junitKey));
        assertTrue(blackDuckServerConfigBuilder.getProperties().containsValue(junitValue), String.format("Should contain value %s", junitValue));
        assertTrue(blackDuckServerConfigBuilder.getProperties().containsKey(BlackDuckServerConfigBuilder.TIMEOUT_KEY), String.format("Should contain %s", BlackDuckServerConfigBuilder.TIMEOUT_KEY));

        IntEnvironmentVariables intEnvironmentVariables = detectEnvironmentService.createDetectEnvironment();
        assertFalse(intEnvironmentVariables.containsKey(junitKey), String.format("Should NOT contain key %s", junitKey));
        assertFalse(intEnvironmentVariables.getVariables().containsValue(junitValue), String.format("Should contain value %s", junitKey));
        assertFalse(intEnvironmentVariables.containsKey(DetectEnvironmentService.TIMEOUT), String.format("Should NOT contain %s", BlackDuckServerConfigBuilder.TIMEOUT_KEY));
    }

    @Test
    public void testEmptyDetectGlobalConfig() {
        Mockito.when(jenkinsConfigServiceMock.getGlobalConfiguration(DetectGlobalConfig.class)).thenReturn(Optional.empty());
        IntEnvironmentVariables intEnvironmentVariables = detectEnvironmentService.createDetectEnvironment();

        assertTrue(intEnvironmentVariables.getVariables().isEmpty(), "Should be an empty map");
    }

    @Test
    public void testEnvironmentAdded() {
        Map<String, String> environmentVariables = new HashMap<>();
        environmentVariables.put(junitKey, junitValue);
        detectEnvironmentService = new DetectEnvironmentService(jenkinsIntLogger, jenkinsProxyHelper, jenkinsVersionHelperMock, synopsysCredentialsHelper, jenkinsConfigServiceMock, environmentVariables);
        IntEnvironmentVariables intEnvironmentVariables = detectEnvironmentService.createDetectEnvironment();

        assertTrue(intEnvironmentVariables.containsKey(junitKey), String.format("Should contain key %s", junitKey));
        assertEquals(junitValue, intEnvironmentVariables.getValue(junitKey), String.format("Value should equal %s", junitValue));
    }

    @Test
    public void testRenameEnvironmentVariable() {
        assertTrue(blackDuckServerConfigBuilder.getProperties().containsKey(BlackDuckServerConfigBuilder.TIMEOUT_KEY), String.format("Should contain %s", BlackDuckServerConfigBuilder.TIMEOUT_KEY));

        IntEnvironmentVariables intEnvironmentVariables = detectEnvironmentService.createDetectEnvironment();
        assertFalse(intEnvironmentVariables.containsKey(BlackDuckServerConfigBuilder.TIMEOUT_KEY.getKey()), String.format("Should NOT contain %s", BlackDuckServerConfigBuilder.TIMEOUT_KEY));
        assertTrue(intEnvironmentVariables.containsKey(DetectEnvironmentService.TIMEOUT), String.format("Should contain key %s", DetectEnvironmentService.TIMEOUT));
    }

}
