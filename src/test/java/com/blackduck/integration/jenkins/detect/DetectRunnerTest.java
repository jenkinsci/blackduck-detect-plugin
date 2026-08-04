package com.blackduck.integration.jenkins.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.blackduck.integration.jenkins.wrapper.BlackduckCredentialsHelper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.blackduck.integration.blackduck.configuration.BlackDuckServerConfigBuilder;
import com.blackduck.integration.builder.BuilderPropertyKey;
import com.blackduck.integration.jenkins.detect.extensions.AirGapDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.DetectDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.ScriptOrJarDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.global.DetectGlobalConfig;
import com.blackduck.integration.jenkins.detect.extensions.tool.DetectAirGapInstallation;
import com.blackduck.integration.jenkins.detect.service.DetectArgumentService;
import com.blackduck.integration.jenkins.detect.service.DetectEnvironmentService;
import com.blackduck.integration.jenkins.detect.service.strategy.DetectAirGapJarStrategy;
import com.blackduck.integration.jenkins.detect.service.strategy.DetectJarStrategy;
import com.blackduck.integration.jenkins.detect.service.strategy.DetectScriptStrategy;
import com.blackduck.integration.jenkins.detect.service.strategy.DetectStrategyService;
import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;
import com.blackduck.integration.jenkins.service.JenkinsConfigService;
import com.blackduck.integration.jenkins.service.JenkinsRemotingService;
import com.blackduck.integration.jenkins.wrapper.JenkinsProxyHelper;
import com.blackduck.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.blackduck.integration.util.IntEnvironmentVariables;
import com.blackduck.integration.util.OperatingSystemType;

class DetectRunnerTest {

    private static final String DETECT_PROPERTY_INPUT = "--detect.docker.passthrough.service.timeout=$DETECT_TIMEOUT --detect.cleanup=false --detect.project.name=\"Test Project'\" --detect.project.tags=alpha,beta,gamma,delta,epsilon";
    private static final String WORKSPACE_TMP_REL_PATH = "out/test/DetectPostBuildStepTest/testPerform/workspace@tmp";
    private static final String JDK_HOME = "/tmp/jdk/bin/java";
    private static final String DETECT_JAR_PATH = "/tmp/detect.jar";
    private static final String DETECT_AIRGAP_JAR_PATH = "/tmp/airgap/detect.jar";
    private static final String DETECT_SHELL_PATH = "/tmp/detect11.sh";
    private static final String DETECT_POWERSHELL_PATH = "/tmp/detect11.ps1";
    private static final String AIRGAP_TOOL_NAME = "AirGap_Tool";
    private static final String AIRGAP_TOOL_PATH = "/air/gap/tool/path";
    private static final AirGapDownloadStrategy AIRGAP_DOWNLOAD_STRATEGY = new AirGapDownloadStrategy();
    private static final ScriptOrJarDownloadStrategy SCRIPTJAR_DOWNLOAD_STRATEGY = new ScriptOrJarDownloadStrategy();

    @Test
    void testRunDetectJar() throws Exception {
        JenkinsRemotingService mockedRemotingService = getMockedRemotingService(OperatingSystemType.LINUX, DETECT_JAR_PATH);
        HashMap<String, String> environment = new HashMap<>();
        environment.put(DetectJenkinsEnvironmentVariable.USER_PROVIDED_JAR_PATH.stringValue(), DETECT_JAR_PATH);

        List<String> actualCommand = runDetectAndCaptureCommand(environment, mockedRemotingService, SCRIPTJAR_DOWNLOAD_STRATEGY);

        int i = 0;
        assertEquals(JDK_HOME, actualCommand.get(i++));
        assertEquals("-jar", actualCommand.get(i++));
        assertEquals(DETECT_JAR_PATH, actualCommand.get(i++));
        assertEquals("--detect.docker.passthrough.service.timeout=120", actualCommand.get(i++));
        assertEquals("--detect.cleanup=false", actualCommand.get(i++));
        assertEquals("--detect.project.name=Test Project'", actualCommand.get(i++));
        assertEquals("--detect.project.tags=alpha,beta,gamma,delta,epsilon", actualCommand.get(i++));
        assertEquals("--logging.level.detect=INFO", actualCommand.get(i++));
        assertTrue(actualCommand.get(i++).startsWith("--detect.phone.home.passthrough.jenkins.version="));
        assertTrue(actualCommand.get(i).startsWith("--detect.phone.home.passthrough.jenkins.plugin.version="));
    }

    @Test
    void testRunDetectShell() throws Exception {
        JenkinsRemotingService mockedRemotingService = getMockedRemotingService(OperatingSystemType.LINUX, DETECT_SHELL_PATH);
        HashMap<String, String> environment = new HashMap<>();

        List<String> actualCommand = runDetectAndCaptureCommand(environment, mockedRemotingService, SCRIPTJAR_DOWNLOAD_STRATEGY);

        int i = 0;
        assertEquals("bash", actualCommand.get(i++));
        assertEquals(DETECT_SHELL_PATH, actualCommand.get(i++));
        assertEquals("--detect.docker.passthrough.service.timeout=120", actualCommand.get(i++));
        assertEquals("--detect.cleanup=false", actualCommand.get(i++));
        assertEquals("--detect.project.name=Test\\ Project\\'", actualCommand.get(i++));
        assertEquals("--detect.project.tags=alpha,beta,gamma,delta,epsilon", actualCommand.get(i++));
        assertEquals("--logging.level.detect=INFO", actualCommand.get(i++));
        assertTrue(actualCommand.get(i++).startsWith("--detect.phone.home.passthrough.jenkins.version="));
        assertTrue(actualCommand.get(i).startsWith("--detect.phone.home.passthrough.jenkins.plugin.version="));
    }

    @Test
    void testRunDetectPowerShell() throws Exception {
        JenkinsRemotingService mockedRemotingService = getMockedRemotingService(OperatingSystemType.WINDOWS, DETECT_POWERSHELL_PATH);
        HashMap<String, String> environment = new HashMap<>();

        List<String> actualCommand = runDetectAndCaptureCommand(environment, mockedRemotingService, SCRIPTJAR_DOWNLOAD_STRATEGY);

        int i = 0;
        assertEquals("powershell", actualCommand.get(i++));
        assertEquals("\"Import-Module '" + DETECT_POWERSHELL_PATH + "'; detect\"", actualCommand.get(i++));
        assertEquals("--detect.docker.passthrough.service.timeout=120", actualCommand.get(i++));
        assertEquals("--detect.cleanup=false", actualCommand.get(i++));
        assertEquals("--detect.project.name=Test` Project`'", actualCommand.get(i++));
        assertEquals("--detect.project.tags=alpha`,beta`,gamma`,delta`,epsilon", actualCommand.get(i++));
        assertEquals("--logging.level.detect=INFO", actualCommand.get(i++));
        assertTrue(actualCommand.get(i++).startsWith("--detect.phone.home.passthrough.jenkins.version="));
        assertTrue(actualCommand.get(i).startsWith("--detect.phone.home.passthrough.jenkins.plugin.version="));
    }

    @Test
    void testRunDetectAirGapJar() throws Exception {
        JenkinsRemotingService mockedRemotingService = getMockedRemotingService(OperatingSystemType.LINUX, DETECT_SHELL_PATH);
        HashMap<String, String> environment = new HashMap<>();

        AirGapDownloadStrategy airGapDownloadStrategySpy = spy(AIRGAP_DOWNLOAD_STRATEGY);
        when(airGapDownloadStrategySpy.getAirGapInstallationName()).thenReturn(AIRGAP_TOOL_NAME);
        List<String> actualCommand = runDetectAndCaptureCommand(environment, mockedRemotingService, airGapDownloadStrategySpy);

        int i = 0;
        assertEquals(JDK_HOME, actualCommand.get(i++));
        assertEquals("-jar", actualCommand.get(i++));
        assertEquals(DETECT_AIRGAP_JAR_PATH, actualCommand.get(i++));
        assertEquals("--detect.docker.passthrough.service.timeout=120", actualCommand.get(i++));
        assertEquals("--detect.cleanup=false", actualCommand.get(i++));
        assertEquals("--detect.project.name=Test Project'", actualCommand.get(i++));
        assertEquals("--detect.project.tags=alpha,beta,gamma,delta,epsilon", actualCommand.get(i++));
        assertEquals("--logging.level.detect=INFO", actualCommand.get(i++));
        assertTrue(actualCommand.get(i++).startsWith("--detect.phone.home.passthrough.jenkins.version="));
        assertTrue(actualCommand.get(i).startsWith("--detect.phone.home.passthrough.jenkins.plugin.version="));
    }

    private JenkinsRemotingService getMockedRemotingService(OperatingSystemType operatingSystemType, String detectPath) throws Exception {
        JenkinsRemotingService mockedRemotingService = mock(JenkinsRemotingService.class);

        when(mockedRemotingService.call(any(DetectJarStrategy.SetupCallableImpl.class)))
            .thenReturn(new ArrayList<>(Arrays.asList(JDK_HOME, "-jar", detectPath)));
        when(mockedRemotingService.call(any(DetectAirGapJarStrategy.SetupCallableImpl.class)))
            .thenReturn(new ArrayList<>(Arrays.asList(JDK_HOME, "-jar", DETECT_AIRGAP_JAR_PATH)));

        if (operatingSystemType == OperatingSystemType.WINDOWS) {
            when(mockedRemotingService.call(any(DetectScriptStrategy.SetupCallableImpl.class)))
                .thenReturn(new ArrayList<>(Arrays.asList("powershell", String.format("\"Import-Module '%s'; detect\"", detectPath))));
        } else {
            when(mockedRemotingService.call(any(DetectScriptStrategy.SetupCallableImpl.class))).thenReturn(new ArrayList<>(Arrays.asList("bash", detectPath)));
        }

        when(mockedRemotingService.getRemoteOperatingSystemType()).thenReturn(operatingSystemType);
        when(mockedRemotingService.launch(any(), any())).thenReturn(0);

        return mockedRemotingService;
    }

    private List<String> runDetectAndCaptureCommand(
        Map<String, String> environmentVariables,
        JenkinsRemotingService mockedRemotingService,
        DetectDownloadStrategy detectDownloadStrategy
    ) throws Exception {
        JenkinsIntLogger jenkinsIntLogger = JenkinsIntLogger.logToListener(null);
        Map<BuilderPropertyKey, String> builderEnvironmentVariables = new HashMap<>();
        builderEnvironmentVariables.put(BlackDuckServerConfigBuilder.TIMEOUT_KEY, "120");

        BlackDuckServerConfigBuilder blackDuckServerConfigBuilder = mock(BlackDuckServerConfigBuilder.class);
        when(blackDuckServerConfigBuilder.getProperties()).thenReturn(builderEnvironmentVariables);

        DetectGlobalConfig detectGlobalConfig = mock(DetectGlobalConfig.class);
        when(detectGlobalConfig.getBlackDuckServerConfigBuilder(any(), any())).thenReturn(blackDuckServerConfigBuilder);

        JenkinsConfigService jenkinsConfigService = mock(JenkinsConfigService.class);
        when(jenkinsConfigService.getGlobalConfiguration(DetectGlobalConfig.class)).thenReturn(Optional.of(detectGlobalConfig));

        // Mocks specific to AirGap
        DetectAirGapInstallation detectAirGapInstallationMock = mock(DetectAirGapInstallation.class);
        when(jenkinsConfigService.getInstallationForNodeAndEnvironment(DetectAirGapInstallation.DescriptorImpl.class, AIRGAP_TOOL_NAME))
            .thenReturn(Optional.ofNullable(detectAirGapInstallationMock));
        doReturn(AIRGAP_TOOL_PATH).when(detectAirGapInstallationMock).getHome();

        JenkinsVersionHelper mockedVersionHelper = mock(JenkinsVersionHelper.class);

        BlackduckCredentialsHelper mockedCredentialsHelper = mock(BlackduckCredentialsHelper.class);

        JenkinsProxyHelper blankProxyHelper = new JenkinsProxyHelper();

        DetectEnvironmentService detectEnvironmentService = new DetectEnvironmentService(
            jenkinsIntLogger,
            blankProxyHelper,
            mockedVersionHelper,
            mockedCredentialsHelper,
            jenkinsConfigService,
            environmentVariables
        );
        DetectArgumentService detectArgumentService = new DetectArgumentService(jenkinsIntLogger, mockedVersionHelper);
        DetectStrategyService detectStrategyService = new DetectStrategyService(jenkinsIntLogger, blankProxyHelper, WORKSPACE_TMP_REL_PATH, jenkinsConfigService);

        DetectRunner detectRunner = new DetectRunner(detectEnvironmentService, mockedRemotingService, detectStrategyService, detectArgumentService, jenkinsIntLogger);

        // run the method we're testing
        detectRunner.runDetect(null, DETECT_PROPERTY_INPUT, detectDownloadStrategy);

        // get the Detect command line that was constructed to return to calling test for validation
        ArgumentCaptor<List<String>> cmdsArgCapture = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<IntEnvironmentVariables> detectEnvCapture = ArgumentCaptor.forClass(IntEnvironmentVariables.class);
        verify(mockedRemotingService).launch(detectEnvCapture.capture(), cmdsArgCapture.capture());

        // verify that the system env is NOT inherited
        // TODO: Verification is needed to check that the system env is not being inherited. A new test should be put in place,
        //       which will only be run if System.getenv().size > 0. In order to do this, detectRunner.runDetect() needs to be
        //       run, which currently requires the setup above. Long term, the tests here should be redesigned so that we aren't
        //       performing all of the mocking. Until then, perform the assert below against all System.getenv() entries.
        System.getenv().forEach((key, value) ->
            assertNotEquals(value, detectEnvCapture.getValue().getValue(value))
        );

        return cmdsArgCapture.getValue();
    }
}
