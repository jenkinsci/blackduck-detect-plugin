package com.blackduck.integration.jenkins.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.blackduck.integration.jenkins.wrapper.BlackduckCredentialsHelper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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
import com.blackduck.integration.jenkins.wrapper.BlackduckCredentialsHelper;
import com.blackduck.integration.util.IntEnvironmentVariables;
import com.blackduck.integration.util.OperatingSystemType;

public class DetectRunnerTest {
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
    public void testRunDetectJar() {
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
    public void testRunDetectShell() {
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
    public void testRunDetectPowerShell() {
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
    public void testRunDetectAirGapJar() {
        JenkinsRemotingService mockedRemotingService = getMockedRemotingService(OperatingSystemType.LINUX, DETECT_SHELL_PATH);
        HashMap<String, String> environment = new HashMap<>();

        AirGapDownloadStrategy airGapDownloadStrategySpy = Mockito.spy(AIRGAP_DOWNLOAD_STRATEGY);
        Mockito.when(airGapDownloadStrategySpy.getAirGapInstallationName()).thenReturn(AIRGAP_TOOL_NAME);
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

    private JenkinsRemotingService getMockedRemotingService(OperatingSystemType operatingSystemType, String detectPath) {
        JenkinsRemotingService mockedRemotingService = Mockito.mock(JenkinsRemotingService.class);

        try {
            Mockito.when(mockedRemotingService.call(Mockito.any(DetectJarStrategy.SetupCallableImpl.class)))
                .thenReturn(new ArrayList<>(Arrays.asList(JDK_HOME, "-jar", detectPath)));
            Mockito.when(mockedRemotingService.call(Mockito.any(DetectAirGapJarStrategy.SetupCallableImpl.class)))
                .thenReturn(new ArrayList<>(Arrays.asList(JDK_HOME, "-jar", DETECT_AIRGAP_JAR_PATH)));

            if (operatingSystemType == OperatingSystemType.WINDOWS) {
                Mockito.when(mockedRemotingService.call(Mockito.any(DetectScriptStrategy.SetupCallableImpl.class)))
                    .thenReturn(new ArrayList<>(Arrays.asList("powershell", String.format("\"Import-Module '%s'; detect\"", detectPath))));
            } else {
                Mockito.when(mockedRemotingService.call(Mockito.any(DetectScriptStrategy.SetupCallableImpl.class))).thenReturn(new ArrayList<>(Arrays.asList("bash", detectPath)));
            }

            Mockito.when(mockedRemotingService.getRemoteOperatingSystemType()).thenReturn(operatingSystemType);
            Mockito.when(mockedRemotingService.launch(Mockito.any(), Mockito.any())).thenReturn(0);
        } catch (Exception e) {
            fail("Could not mock JenkinsRemotingService due to an unexpected exception. The test code likely requires fixing: ", e);
        }

        return mockedRemotingService;
    }

    private List<String> runDetectAndCaptureCommand(
        Map<String, String> environmentVariables,
        JenkinsRemotingService mockedRemotingService,
        DetectDownloadStrategy detectDownloadStrategy
    ) {
        try {
            JenkinsIntLogger jenkinsIntLogger = JenkinsIntLogger.logToListener(null);
            Map<BuilderPropertyKey, String> builderEnvironmentVariables = new HashMap<>();
            builderEnvironmentVariables.put(BlackDuckServerConfigBuilder.TIMEOUT_KEY, "120");

            BlackDuckServerConfigBuilder blackDuckServerConfigBuilder = Mockito.mock(BlackDuckServerConfigBuilder.class);
            Mockito.when(blackDuckServerConfigBuilder.getProperties()).thenReturn(builderEnvironmentVariables);

            DetectGlobalConfig detectGlobalConfig = Mockito.mock(DetectGlobalConfig.class);
            Mockito.when(detectGlobalConfig.getBlackDuckServerConfigBuilder(Mockito.any(), Mockito.any())).thenReturn(blackDuckServerConfigBuilder);

            JenkinsConfigService jenkinsConfigService = Mockito.mock(JenkinsConfigService.class);
            Mockito.when(jenkinsConfigService.getGlobalConfiguration(DetectGlobalConfig.class)).thenReturn(Optional.of(detectGlobalConfig));

            // Mocks specific to AirGap
            DetectAirGapInstallation detectAirGapInstallationMock = Mockito.mock(DetectAirGapInstallation.class);
            Mockito.when(jenkinsConfigService.getInstallationForNodeAndEnvironment(DetectAirGapInstallation.DescriptorImpl.class, AIRGAP_TOOL_NAME))
                .thenReturn(Optional.ofNullable(detectAirGapInstallationMock));
            Mockito.doReturn(AIRGAP_TOOL_PATH).when(detectAirGapInstallationMock).getHome();

            JenkinsVersionHelper mockedVersionHelper = Mockito.mock(JenkinsVersionHelper.class);

            BlackduckCredentialsHelper mockedCredentialsHelper = Mockito.mock(BlackduckCredentialsHelper.class);

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
            Mockito.verify(mockedRemotingService).launch(detectEnvCapture.capture(), cmdsArgCapture.capture());

            // verify that the system env is NOT inherited
            // TODO: Verification is needed to check that the system env is not being inherited. A new test should be put in place,
            //       which will only be run if System.getenv().size > 0. In order to do this, detectRunner.runDetect() needs to be
            //       run, which currently requires the setup above. Long term, the tests here should be redesigned so that we aren't
            //       performing all of the mocking. Until then, perform the assert below against all System.getenv() entries.
            System.getenv().forEach((key, value) ->
                assertNotEquals(value, detectEnvCapture.getValue().getValue(value))
            );

            return cmdsArgCapture.getValue();
        } catch (Throwable t) {
            fail("An unexpected exception was thrown by the test code: ", t);
        }
        return Collections.emptyList();
    }
}
