package com.synopsys.integration.jenkins.detect.service.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.detect.service.DetectArgumentService;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.service.JenkinsRemotingService;
import com.synopsys.integration.jenkins.wrapper.JenkinsProxyHelper;
import com.synopsys.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.synopsys.integration.util.IntEnvironmentVariables;
import com.synopsys.integration.util.OperatingSystemType;

import hudson.model.TaskListener;

public class DetectScriptStrategyCallableTest {
    private JenkinsIntLogger logger;
    private JenkinsProxyHelper defaultProxyHelper;
    private ByteArrayOutputStream byteArrayOutputStream;
    private String toolsDirectoryPath;
    private JenkinsRemotingService jenkinsRemotingServiceMock;
    private IntEnvironmentVariables intEnvironmentVariables;
    private DetectArgumentService detectArgumentService;

    @BeforeEach
    public void setUp() {
        byteArrayOutputStream = new ByteArrayOutputStream();
        TaskListener mockedTaskListener = Mockito.mock(TaskListener.class);
        Mockito.when(mockedTaskListener.getLogger()).thenReturn(new PrintStream(byteArrayOutputStream));
        logger = new JenkinsIntLogger(mockedTaskListener);
        defaultProxyHelper = new JenkinsProxyHelper();

        try {
            File downloadDirectory = Files.createTempDirectory("testDetectScriptStrategy").toFile();
            downloadDirectory.deleteOnExit();
            toolsDirectoryPath = downloadDirectory.getCanonicalPath();
        } catch (IOException e) {
            assumeTrue(false, "Skipping test, could not create temporary directory: " + e.getMessage());
        }

        JenkinsVersionHelper jenkinsVersionHelperMock = Mockito.mock(JenkinsVersionHelper.class);
        detectArgumentService = new DetectArgumentService(logger, jenkinsVersionHelperMock);
        jenkinsRemotingServiceMock = Mockito.mock(JenkinsRemotingService.class);
        intEnvironmentVariables = IntEnvironmentVariables.empty();
    }

    @AfterEach
    public void cleanUp() {
        try {
            FileUtils.deleteDirectory(new File(toolsDirectoryPath));
        } catch (IOException e) {
            fail("Clean up failed: ", e);
        }
    }

    @Test
    public void testDownloadShellScript() {
        downloadAndValidateScript(OperatingSystemType.LINUX);
    }

    @Test
    public void testDownloadPowershellScript() {
        downloadAndValidateScript(OperatingSystemType.WINDOWS);
    }

    @Test
    public void testFailureToDownload() {
        assumeTrue(new File(toolsDirectoryPath).setReadOnly(), "Skipping test because we can't modify file permissions.");
        assumeFalse(new File(toolsDirectoryPath).canWrite(), "Skipping as test can still write. Possibly running as root.");

        DetectScriptStrategy detectScriptStrategy = new DetectScriptStrategy(jenkinsRemotingServiceMock, detectArgumentService, intEnvironmentVariables, logger, defaultProxyHelper, OperatingSystemType.determineFromSystem(),
            toolsDirectoryPath);

        try {
            assertThrows(IntegrationException.class, detectScriptStrategy.getSetupCallable()::call);
        } catch (IntegrationException e) {
            fail("An unexpected exception occurred: ", e);
        }
    }

    @Test
    public void testAlreadyExists() {
        try {
            String scriptName = (SystemUtils.IS_OS_WINDOWS) ? DetectScriptStrategy.POWERSHELL_SCRIPT_FILENAME : DetectScriptStrategy.SHELL_SCRIPT_FILENAME;
            Path preDownloadedShellScript = Paths.get(toolsDirectoryPath, DetectScriptStrategy.DETECT_INSTALL_DIRECTORY);
            Files.createDirectories(preDownloadedShellScript);
            Files.createFile(preDownloadedShellScript.resolve(scriptName));
        } catch (Exception e) {
            fail("Test could not be set up: Could not create Shell Script file", e);
        }

        try {
            DetectScriptStrategy detectScriptStrategy = new DetectScriptStrategy(jenkinsRemotingServiceMock, detectArgumentService, intEnvironmentVariables, logger, defaultProxyHelper, OperatingSystemType.determineFromSystem(),
                toolsDirectoryPath);
            detectScriptStrategy.getSetupCallable().call();

            assertTrue(byteArrayOutputStream.toString().contains("Running already installed Detect script"), "Expected a message about running an existing Detect script but found none.");
        } catch (IntegrationException e) {
            fail("Unexpected exception occurred: ", e);
        }
    }

    private void downloadAndValidateScript(OperatingSystemType operatingSystemType) {
        try {
            String expectedScriptPath = new File(toolsDirectoryPath, DetectScriptStrategy.DETECT_INSTALL_DIRECTORY).getPath();

            DetectScriptStrategy detectScriptStrategy = new DetectScriptStrategy(jenkinsRemotingServiceMock, detectArgumentService, intEnvironmentVariables, logger, defaultProxyHelper, operatingSystemType, toolsDirectoryPath);
            ArrayList<String> scriptStrategyArgs = detectScriptStrategy.getSetupCallable().call();
            File remoteScriptFile = new File(parseScriptStrategyArgs(scriptStrategyArgs));

            assertEquals(expectedScriptPath, remoteScriptFile.getParent(), String.format("Script was not downloaded to <%s>", expectedScriptPath));
            assertTrue(remoteScriptFile.exists(), String.format("Expected script does not exist <%s>", expectedScriptPath));
        } catch (IntegrationException e) {
            fail("Unexpected exception occurred: ", e);
        }
    }

    private String parseScriptStrategyArgs(ArrayList<String> scriptStrategyArgs) {
        String remoteScriptArgument = scriptStrategyArgs.get(scriptStrategyArgs.size() - 1);
        if (scriptStrategyArgs.get(0).equals("powershell")) {
            remoteScriptArgument = remoteScriptArgument.split("'")[1];
        }
        return remoteScriptArgument;
    }
}
