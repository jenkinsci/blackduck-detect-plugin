package com.blackduck.integration.jenkins.detect.service.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.blackduck.integration.jenkins.detect.DetectJenkinsEnvironmentVariable;
import com.blackduck.integration.jenkins.detect.exception.DetectJenkinsException;
import com.blackduck.integration.jenkins.detect.extensions.AirGapDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.DetectDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.InheritFromGlobalDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.ScriptOrJarDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.global.DetectGlobalConfig;
import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;
import com.blackduck.integration.jenkins.service.JenkinsConfigService;
import com.blackduck.integration.util.IntEnvironmentVariables;

import hudson.model.TaskListener;

class DetectStrategyServiceTest {

    private static final AirGapDownloadStrategy AIRGAP_DOWNLOAD_STRATEGY = new AirGapDownloadStrategy();
    private static final InheritFromGlobalDownloadStrategy INHERIT_DOWNLOAD_STRATEGY = new InheritFromGlobalDownloadStrategy();
    private static final ScriptOrJarDownloadStrategy SCRIPTJAR_DOWNLOAD_STRATEGY = new ScriptOrJarDownloadStrategy();

    private final IntEnvironmentVariables intEnvironmentVariables = IntEnvironmentVariables.empty();

    private ByteArrayOutputStream byteArrayOutputStream;
    private DetectStrategyService detectStrategyService;
    private JenkinsConfigService jenkinsConfigService;

    @BeforeEach
    void setup() {
        TaskListener taskListener = mock(TaskListener.class);
        byteArrayOutputStream = new ByteArrayOutputStream();
        when(taskListener.getLogger()).thenReturn(new PrintStream(byteArrayOutputStream));
        JenkinsIntLogger logger = JenkinsIntLogger.logToListener(taskListener);

        jenkinsConfigService = mock(JenkinsConfigService.class);
        detectStrategyService = new DetectStrategyService(logger, null, null, jenkinsConfigService);
    }

    @Test
    void testGettersAndSetters() {
        assertNull(AIRGAP_DOWNLOAD_STRATEGY.getAirGapInstallationName());
        AIRGAP_DOWNLOAD_STRATEGY.setAirGapInstallationName("JUnit_AirGap_Tool");
        assertEquals("JUnit_AirGap_Tool", AIRGAP_DOWNLOAD_STRATEGY.getAirGapInstallationName());
        assertEquals(AirGapDownloadStrategy.DISPLAY_NAME, AIRGAP_DOWNLOAD_STRATEGY.getDisplayName());

        assertEquals(InheritFromGlobalDownloadStrategy.DISPLAY_NAME, INHERIT_DOWNLOAD_STRATEGY.getDisplayName());

        assertEquals(ScriptOrJarDownloadStrategy.DISPLAY_NAME, SCRIPTJAR_DOWNLOAD_STRATEGY.getDisplayName());
    }

    @Test
    void testInheritFromGlobalStrategy() throws Exception {
        DetectGlobalConfig mockDetectGlobalConfig = mock(DetectGlobalConfig.class);
        when(jenkinsConfigService.getGlobalConfiguration(DetectGlobalConfig.class)).thenReturn(Optional.ofNullable(mockDetectGlobalConfig));
        assertNotNull(mockDetectGlobalConfig);
        when(mockDetectGlobalConfig.getDownloadStrategy()).thenReturn(AIRGAP_DOWNLOAD_STRATEGY);

        DetectExecutionStrategy detectExecutionStrategy = testGetExecutionStrategy(intEnvironmentVariables, INHERIT_DOWNLOAD_STRATEGY);
        assertEquals(DetectAirGapJarStrategy.class, detectExecutionStrategy.getClass());
        assertTrue(byteArrayOutputStream.toString().contains(AirGapDownloadStrategy.DISPLAY_NAME), "Log does not contain message with correct download strategy.");
    }

    @Test
    void testInheritDefaultGlobalStrategy() throws Exception {
        DetectGlobalConfig mockDetectGlobalConfig = mock(DetectGlobalConfig.class);
        when(jenkinsConfigService.getGlobalConfiguration(DetectGlobalConfig.class)).thenReturn(Optional.ofNullable(mockDetectGlobalConfig));
        assertNotNull(mockDetectGlobalConfig);
        when(mockDetectGlobalConfig.getDownloadStrategy()).thenReturn(null);
        when(mockDetectGlobalConfig.getDefaultDownloadStrategy()).thenReturn(SCRIPTJAR_DOWNLOAD_STRATEGY);

        DetectExecutionStrategy detectExecutionStrategy = testGetExecutionStrategy(intEnvironmentVariables, INHERIT_DOWNLOAD_STRATEGY);
        assertEquals(DetectScriptStrategy.class, detectExecutionStrategy.getClass());
        assertTrue(byteArrayOutputStream.toString().contains(ScriptOrJarDownloadStrategy.DISPLAY_NAME), "Log does not contain message with correct download strategy.");
    }

    @Test
    void testInheritFromGlobalStrategyFailure() {
        assertThrows(DetectJenkinsException.class, () -> detectStrategyService.getExecutionStrategy(intEnvironmentVariables, null, null, INHERIT_DOWNLOAD_STRATEGY));
    }

    @Test
    void testNullStrategyFailure() {
        assertThrows(DetectJenkinsException.class, () -> detectStrategyService.getExecutionStrategy(intEnvironmentVariables, null, null, null));
    }

    @Test
    void testGetAirGapJarStrategy() throws Exception {
        DetectExecutionStrategy detectExecutionStrategy = testGetExecutionStrategy(intEnvironmentVariables, AIRGAP_DOWNLOAD_STRATEGY);
        assertEquals(DetectAirGapJarStrategy.class, detectExecutionStrategy.getClass());
        assertTrue(byteArrayOutputStream.toString().contains(AirGapDownloadStrategy.DISPLAY_NAME), "Log does not contain message with correct download strategy.");
    }

    @Test
    void testGetJarStrategy() throws Exception {
        intEnvironmentVariables.put(DetectJenkinsEnvironmentVariable.USER_PROVIDED_JAR_PATH.stringValue(), "/tmp/path/to/detect.jar");

        DetectExecutionStrategy detectExecutionStrategy = testGetExecutionStrategy(intEnvironmentVariables, SCRIPTJAR_DOWNLOAD_STRATEGY);
        assertEquals(DetectJarStrategy.class, detectExecutionStrategy.getClass());
        assertTrue(byteArrayOutputStream.toString().contains(ScriptOrJarDownloadStrategy.DISPLAY_NAME), "Log does not contain message with correct download strategy.");
    }

    @Test
    void testGetScriptStrategy() throws Exception {
        DetectExecutionStrategy detectExecutionStrategy = testGetExecutionStrategy(intEnvironmentVariables, SCRIPTJAR_DOWNLOAD_STRATEGY);
        assertEquals(DetectScriptStrategy.class, detectExecutionStrategy.getClass());
        assertTrue(byteArrayOutputStream.toString().contains(ScriptOrJarDownloadStrategy.DISPLAY_NAME), "Log does not contain message with correct download strategy.");
    }

    private DetectExecutionStrategy testGetExecutionStrategy(IntEnvironmentVariables intEnvironmentVariables, DetectDownloadStrategy downloadStrategy) throws Exception {
        return detectStrategyService.getExecutionStrategy(intEnvironmentVariables, null, null, downloadStrategy);
    }
}
