package com.blackduck.integration.jenkins.detect.service.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import com.blackduck.integration.exception.IntegrationException;
import com.blackduck.integration.jenkins.detect.exception.DetectJenkinsException;
import com.blackduck.integration.jenkins.detect.extensions.AirGapDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.tool.DetectAirGapInstallation;
import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;
import com.blackduck.integration.jenkins.service.JenkinsConfigService;
import com.blackduck.integration.log.LogLevel;
import com.blackduck.integration.util.IntEnvironmentVariables;

import hudson.model.TaskListener;
import jenkins.security.MasterToSlaveCallable;

public class DetectAirGapJarStrategyTest {
    private static final String JAVA_EXECUTABLE = (SystemUtils.IS_OS_WINDOWS) ? "java.exe" : "java";
    private static final String REMOTE_JDK_HOME = new File("/test/java/home/path").getAbsolutePath();
    private static final String REMOTE_JAVA_RELATIVE_PATH = new File("/bin/" + JAVA_EXECUTABLE).getPath();
    private static final String EXPECTED_JAVA_FULL_PATH = new File(REMOTE_JDK_HOME + REMOTE_JAVA_RELATIVE_PATH).getAbsolutePath();
    private static final String EXPECTED_PATH = new File("/test/path/env").getAbsolutePath();

    private static final String AIRGAP_TOOL_NAME = "DetectAirGapTool";
    private static final String EXPECTED_ONE_JAR_ERROR_MSG = "Expected 1 jar from Detect Air Gap tool installation at <%s>";
    private static final AirGapDownloadStrategy AIRGAP_DOWNLOAD_STRATEGY = new AirGapDownloadStrategy();

    private final JenkinsConfigService jenkinsConfigServiceMock = Mockito.mock(JenkinsConfigService.class);
    private final DetectAirGapInstallation detectAirGapInstallationMock = Mockito.mock(DetectAirGapInstallation.class);

    private IntEnvironmentVariables environmentVariables;
    private JenkinsIntLogger logger;
    private ByteArrayOutputStream byteArrayOutputStream;
    private String tempJarDirectoryPathName;
    private File tempAirGapJar;

    public static Stream<Arguments> testJavaHomeSource() {
        return Stream.of(
            Arguments.of(" ", System.getProperty("user.dir") + File.separator + " " + REMOTE_JAVA_RELATIVE_PATH),
            Arguments.of("", new File(REMOTE_JAVA_RELATIVE_PATH).getAbsolutePath()),
            Arguments.of(null, JAVA_EXECUTABLE),
            Arguments.of(REMOTE_JDK_HOME, EXPECTED_JAVA_FULL_PATH)
        );
    }

    @BeforeEach
    public void setup() throws IOException, InterruptedException {
        environmentVariables = IntEnvironmentVariables.empty();
        environmentVariables.put("PATH", EXPECTED_PATH);

        TaskListener taskListener = Mockito.mock(TaskListener.class);
        byteArrayOutputStream = new ByteArrayOutputStream();
        Mockito.when(taskListener.getLogger()).thenReturn(new PrintStream(byteArrayOutputStream));

        logger = JenkinsIntLogger.logToListener(taskListener);

        tempJarDirectoryPathName = createTempAirGapDirectory().getPath();
        tempAirGapJar = createTempAirGapJar(DetectAirGapJarStrategy.DETECT_JAR_PREFIX, DetectAirGapJarStrategy.DETECT_JAR_SUFFIX);

        Mockito.doReturn(Optional.of(detectAirGapInstallationMock)).when(jenkinsConfigServiceMock)
            .getInstallationForNodeAndEnvironment(DetectAirGapInstallation.DescriptorImpl.class, AIRGAP_TOOL_NAME);
    }

    @Test
    public void testArgumentEscaper() {
        DetectAirGapJarStrategy detectAirGapJarStrategy = new DetectAirGapJarStrategy(
            logger,
            environmentVariables,
            REMOTE_JDK_HOME,
            jenkinsConfigServiceMock,
            AIRGAP_DOWNLOAD_STRATEGY
        );
        assertEquals(Function.identity(), detectAirGapJarStrategy.getArgumentEscaper());
    }

    @ParameterizedTest
    @MethodSource("testJavaHomeSource")
    public void testJavaHome(String javaHome, String expectedJavaPath) {
        executeAndValidateSetupCallable(javaHome, expectedJavaPath, tempJarDirectoryPathName, tempAirGapJar);
    }

    @Test
    public void testWarnLogging() {
        logger.setLogLevel(LogLevel.WARN);
        executeAndValidateSetupCallable(REMOTE_JDK_HOME, EXPECTED_JAVA_FULL_PATH, tempJarDirectoryPathName, tempAirGapJar);
        validateLogsNotPresentInfo();
        validateLogsNotPresentDebug();
    }

    @Test
    public void testInfoLogging() {
        logger.setLogLevel(LogLevel.INFO);
        executeAndValidateSetupCallable(REMOTE_JDK_HOME, EXPECTED_JAVA_FULL_PATH, tempJarDirectoryPathName, tempAirGapJar);
        validateLogsPresentInfo();
        validateLogsNotPresentDebug();
    }

    @Test
    public void testDebugLogging() {
        logger.setLogLevel(LogLevel.DEBUG);
        executeAndValidateSetupCallable(REMOTE_JDK_HOME, EXPECTED_JAVA_FULL_PATH, tempJarDirectoryPathName, tempAirGapJar);
        validateLogsPresentInfo();
        validateLogsPresentDebug();
    }

    @Test
    public void testTraceLogging() {
        logger.setLogLevel(LogLevel.TRACE);
        executeAndValidateSetupCallable(REMOTE_JDK_HOME, EXPECTED_JAVA_FULL_PATH, tempJarDirectoryPathName, tempAirGapJar);
        validateLogsPresentInfo();
        validateLogsPresentDebug();
    }

    @Test
    public void testDebugLoggingJavaVersionFailed() {
        logger.setLogLevel(LogLevel.DEBUG);
        try {
            String badJavaHome = Files.createTempDirectory(null).toRealPath().toString();
            String expectedBadJavaPath = badJavaHome + REMOTE_JAVA_RELATIVE_PATH;
            executeAndValidateSetupCallable(badJavaHome, expectedBadJavaPath, tempJarDirectoryPathName, tempAirGapJar);

            String expectedError = (SystemUtils.IS_OS_WINDOWS) ? "The system cannot find the file specified" : "No such file or directory";
            assertTrue(byteArrayOutputStream.toString().contains(expectedError), "Log does not contain error for starting process.");
        } catch (IOException e) {
            fail("Unexpected exception was thrown in test code: ", e);
        }
    }

    @Test
    public void testDebugLoggingJavaVersionSuccess() {
        logger.setLogLevel(LogLevel.DEBUG);
        executeAndValidateSetupCallable(null, JAVA_EXECUTABLE, tempJarDirectoryPathName, tempAirGapJar);

        assertTrue(byteArrayOutputStream.toString().contains("Java version: "), "Log does not contain entry for Java Version heading.");
    }

    @Test
    public void testNullToolName() {
        try {
            Mockito.doReturn(Optional.empty()).when(jenkinsConfigServiceMock).getInstallationForNodeAndEnvironment(DetectAirGapInstallation.DescriptorImpl.class, AIRGAP_TOOL_NAME);
        } catch (Exception e) {
            fail("Unexpected exception mocking call to getInstallationForNodeAndEnvironment");
        }

        DetectJenkinsException exception = assertThrows(DetectJenkinsException.class, () -> configureCallable(REMOTE_JDK_HOME, tempJarDirectoryPathName).getSetupCallable().call());
        assertTrue(
            exception.getMessage().contains(String.format("Problem encountered getting Detect Air Gap tool with the name %s from global tool configuration.", AIRGAP_TOOL_NAME)),
            "Stacktrace does not contain expected message: " + exception.getMessage()
        );
    }

    @Test
    public void testIOExceptionGetToolName() {
        try {
            Mockito.doThrow(IOException.class).when(jenkinsConfigServiceMock).getInstallationForNodeAndEnvironment(DetectAirGapInstallation.DescriptorImpl.class, AIRGAP_TOOL_NAME);
        } catch (Exception e) {
            fail("Unexpected exception mocking call to getInstallationForNodeAndEnvironment");
        }

        DetectJenkinsException exception = assertThrows(DetectJenkinsException.class, () -> configureCallable(REMOTE_JDK_HOME, tempJarDirectoryPathName).getSetupCallable().call());
        assertTrue(exception.getCause() instanceof IOException, "Expected an IOException to be thrown");
        assertTrue(
            exception.getMessage().contains("Problem encountered while interacting with Jenkins environment."),
            "Stacktrace does not contain expected message: " + exception.getMessage()
        );
    }

    @Test
    public void testInterruptedExceptionGetToolName() {
        try {
            Mockito.doThrow(InterruptedException.class).when(jenkinsConfigServiceMock)
                .getInstallationForNodeAndEnvironment(DetectAirGapInstallation.DescriptorImpl.class, AIRGAP_TOOL_NAME);
        } catch (Exception e) {
            fail("Unexpected exception mocking call to getInstallationForNodeAndEnvironment");
        }

        DetectJenkinsException exception = assertThrows(DetectJenkinsException.class, () -> configureCallable(REMOTE_JDK_HOME, tempJarDirectoryPathName).getSetupCallable().call());
        assertTrue(exception.getCause() instanceof InterruptedException, "Expected an InterruptedException to be thrown");
        assertTrue(exception.getMessage().contains("Getting Detect Air Gap tool was interrupted."), "Stacktrace does not contain expected message: " + exception.getMessage());
    }

    @Test
    public void testNullAirGapHome() {
        DetectJenkinsException exception = assertThrows(DetectJenkinsException.class, () -> configureCallable(REMOTE_JDK_HOME, null).getSetupCallable().call());
        assertTrue(exception.getMessage().contains("Detect AirGap installation directory is null."), "Stacktrace does not contain expected message.");
    }

    @Test
    public void testEmptyAirGapHome() {
        DetectJenkinsException exception = assertThrows(DetectJenkinsException.class, () -> configureCallable(REMOTE_JDK_HOME, "").getSetupCallable().call());
        assertTrue(exception.getMessage().contains(String.format(EXPECTED_ONE_JAR_ERROR_MSG, "")), "Stacktrace does not contain expected message.");
    }

    @Test
    public void testNoJarFound() {
        assertTrue(tempAirGapJar.delete(), "Pre-clean for no jar found test failed");

        DetectJenkinsException exception = assertThrows(DetectJenkinsException.class, () -> configureCallable(REMOTE_JDK_HOME, tempJarDirectoryPathName).getSetupCallable().call());
        assertTrue(exception.getMessage().contains(String.format(EXPECTED_ONE_JAR_ERROR_MSG, tempJarDirectoryPathName)), "Stacktrace does not contain expected message.");
    }

    @Test
    public void testNoJarPrefixFound() {
        assertTrue(tempAirGapJar.delete(), "Pre-clean for no jar (prefix) found test failed");
        createTempAirGapJar("dummy-", DetectAirGapJarStrategy.DETECT_JAR_SUFFIX);

        DetectJenkinsException exception = assertThrows(DetectJenkinsException.class, () -> configureCallable(REMOTE_JDK_HOME, tempJarDirectoryPathName).getSetupCallable().call());
        assertTrue(exception.getMessage().contains(String.format(EXPECTED_ONE_JAR_ERROR_MSG, tempJarDirectoryPathName)), "Stacktrace does not contain expected message.");
    }

    @Test
    public void testNoJarSuffixFound() {
        assertTrue(tempAirGapJar.delete(), "Pre-clean for no jar (suffix) found test failed");
        createTempAirGapJar(DetectAirGapJarStrategy.DETECT_JAR_PREFIX, ".dummy");

        DetectJenkinsException exception = assertThrows(DetectJenkinsException.class, () -> configureCallable(REMOTE_JDK_HOME, tempJarDirectoryPathName).getSetupCallable().call());
        assertTrue(exception.getMessage().contains(String.format(EXPECTED_ONE_JAR_ERROR_MSG, tempJarDirectoryPathName)), "Stacktrace does not contain expected message.");
    }

    @Test
    public void testMultipleJarsFound() {
        // Single jar file was created during setup()
        createTempAirGapJar(DetectAirGapJarStrategy.DETECT_JAR_PREFIX, DetectAirGapJarStrategy.DETECT_JAR_SUFFIX);

        DetectJenkinsException exception = assertThrows(DetectJenkinsException.class, () -> configureCallable(REMOTE_JDK_HOME, tempJarDirectoryPathName).getSetupCallable().call());
        assertTrue(
            exception.getMessage().contains(String.format(EXPECTED_ONE_JAR_ERROR_MSG + " and instead found multiple jars", tempJarDirectoryPathName, 2)),
            "Stacktrace does not contain expected message."
        );
    }

    private void executeAndValidateSetupCallable(String javaHomeInput, String expectedJavaPath, String toolHomeDirectory, File expectedAirGapJar) {
        try {
            DetectAirGapJarStrategy detectAirGapJarStrategy = configureCallable(javaHomeInput, toolHomeDirectory);
            MasterToSlaveCallable<ArrayList<String>, IntegrationException> setupCallable = detectAirGapJarStrategy.getSetupCallable();
            ArrayList<String> airGapJarExecutionElements = setupCallable.call();
            String resolvedExpectedJavaPath = resolveDirectory(expectedJavaPath);

            assertEquals(resolvedExpectedJavaPath, airGapJarExecutionElements.get(0));
            assertEquals("-jar", airGapJarExecutionElements.get(1));
            assertEquals(expectedAirGapJar.getPath(), airGapJarExecutionElements.get(2));
        } catch (IntegrationException e) {
            fail("An unexpected exception occurred: ", e);
        }
    }

    private String resolveDirectory(String inputDirectory) {
        try {
            return Paths.get(inputDirectory).toRealPath().toString();
        } catch (IOException | InvalidPathException e) {
            return inputDirectory;
        }
    }

    private DetectAirGapJarStrategy configureCallable(String javaHomeInput, String toolHomeDirectory) {
        AirGapDownloadStrategy spiedAirGapDownloadStrategy = Mockito.spy(AIRGAP_DOWNLOAD_STRATEGY);
        Mockito.when(spiedAirGapDownloadStrategy.getAirGapInstallationName()).thenReturn(AIRGAP_TOOL_NAME);
        Mockito.when(detectAirGapInstallationMock.getHome()).thenReturn(toolHomeDirectory);
        return new DetectAirGapJarStrategy(logger, environmentVariables, javaHomeInput, jenkinsConfigServiceMock, spiedAirGapDownloadStrategy);
    }

    private void validateLogsNotPresentInfo() {
        assertFalse(byteArrayOutputStream.toString().contains("Running with JAVA: "), "Log contains entry for JAVA path and shouldn't.");
        assertFalse(byteArrayOutputStream.toString().contains("Detect AirGap jar configured: "), "Log contains entry for Detect path and shouldn't.");
    }

    private void validateLogsNotPresentDebug() {
        assertFalse(byteArrayOutputStream.toString().contains("PATH: "), "Log contains entry for PATH environment variable and shouldn't.");
    }

    private void validateLogsPresentInfo() {
        assertTrue(byteArrayOutputStream.toString().contains("Running with JAVA: "), "Log does not contain entry for JAVA path.");
        assertTrue(byteArrayOutputStream.toString().contains("Detect AirGap jar configured: "), "Log does not contain entry for Detect path.");
    }

    private void validateLogsPresentDebug() {
        assertTrue(byteArrayOutputStream.toString().contains("PATH: "), "Log does not contain entry for PATH environment variable.");
    }

    private File createTempAirGapDirectory() {
        File tempJarDirectory = null;
        try {
            tempJarDirectory = Files.createTempDirectory("Test-AirGapJar-Strategy").toFile();
            tempJarDirectory.deleteOnExit();
            System.out.printf("Test directory created: %s%n", tempJarDirectory.getPath());
        } catch (IOException e) {
            fail("Unexpected exception was thrown in test code: ", e);
        }
        return tempJarDirectory;
    }

    private File createTempAirGapJar(String prefix, String suffix) {
        File tempAirGapJar = null;
        try {
            tempAirGapJar = File.createTempFile(prefix, suffix, new File(tempJarDirectoryPathName));
            tempAirGapJar.deleteOnExit();
            System.out.printf("Test jar created: %s%n", tempAirGapJar.getName());
        } catch (IOException e) {
            fail("Unexpected exception was thrown in test code: ", e);
        }
        return tempAirGapJar;
    }
}
