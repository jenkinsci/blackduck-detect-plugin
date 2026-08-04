package com.blackduck.integration.jenkins.detect.service.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

import com.blackduck.integration.exception.IntegrationException;
import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;
import com.blackduck.integration.jenkins.wrapper.JenkinsProxyHelper;
import com.blackduck.integration.util.OperatingSystemType;

import hudson.model.TaskListener;

class DetectScriptStrategyCallableTest {

    private JenkinsIntLogger defaultLogger;
    private JenkinsProxyHelper defaultProxyHelper;
    private String toolsDirectoryPath;

    @BeforeEach
    void setup() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        TaskListener mockedTaskListener = mock(TaskListener.class);
        when(mockedTaskListener.getLogger()).thenReturn(new PrintStream(byteArrayOutputStream));
        defaultLogger = JenkinsIntLogger.logToListener(mockedTaskListener);
        defaultProxyHelper = new JenkinsProxyHelper();

        try {
            File downloadDirectory = Files.createTempDirectory("testDetectScriptStrategy").toFile();
            downloadDirectory.deleteOnExit();
            toolsDirectoryPath = downloadDirectory.getCanonicalPath();
        } catch (IOException e) {
            assumeTrue(false, "Skipping test, could not create temporary directory: " + e.getMessage());
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        FileUtils.deleteDirectory(new File(toolsDirectoryPath));
    }

    @Test
    void testDownloadShellScript() throws Exception {
        downloadAndValidateScript(OperatingSystemType.LINUX);
    }

    @Test
    void testDownloadPowershellScript() throws Exception {
        downloadAndValidateScript(OperatingSystemType.WINDOWS);
    }

    @Test
    void testFailureToDownload() throws Exception {
        assumeTrue(new File(toolsDirectoryPath).setReadOnly(), "Skipping test because we can't modify file permissions.");
        assumeFalse(new File(toolsDirectoryPath).canWrite(), "Skipping as test can still write. Possibly running as root.");

        DetectScriptStrategy detectScriptStrategy = new DetectScriptStrategy(
            defaultLogger,
            defaultProxyHelper,
            OperatingSystemType.determineFromSystem(),
            toolsDirectoryPath
        );

        assertThrows(IntegrationException.class, detectScriptStrategy.getSetupCallable()::call);
    }

    @Test
    void testAlreadyExists() throws Exception {
        String scriptName = (SystemUtils.IS_OS_WINDOWS) ? DetectScriptStrategy.POWERSHELL_SCRIPT_FILENAME : DetectScriptStrategy.SHELL_SCRIPT_FILENAME;
        Path preDownloadedShellScript = Paths.get(toolsDirectoryPath, DetectScriptStrategy.DETECT_INSTALL_DIRECTORY);
        Files.createDirectories(preDownloadedShellScript);
        Files.createFile(preDownloadedShellScript.resolve(scriptName));

        downloadAndValidateScript(OperatingSystemType.determineFromSystem());
    }

    private void downloadAndValidateScript(OperatingSystemType operatingSystemType) throws Exception {
        String expectedScriptPath = new File(toolsDirectoryPath, DetectScriptStrategy.DETECT_INSTALL_DIRECTORY).getPath();

        DetectScriptStrategy detectScriptStrategy = new DetectScriptStrategy(defaultLogger, defaultProxyHelper, operatingSystemType, toolsDirectoryPath);
        ArrayList<String> scriptStrategyArgs = detectScriptStrategy.getSetupCallable().call();
        File remoteScriptFile = new File(parseScriptStrategyArgs(scriptStrategyArgs));

        assertEquals(expectedScriptPath, remoteScriptFile.getParent(), String.format("Script was not downloaded to <%s>", expectedScriptPath));
        assertTrue(remoteScriptFile.exists(), String.format("Expected script does not exist <%s>", expectedScriptPath));
        assertTrue(Files.size(remoteScriptFile.toPath()) > 0, String.format("Expected script exists, but it's empty <%s>", expectedScriptPath));
    }

    private String parseScriptStrategyArgs(ArrayList<String> scriptStrategyArgs) {
        String remoteScriptArgument = scriptStrategyArgs.get(scriptStrategyArgs.size() - 1);
        if (scriptStrategyArgs.get(0).equals("powershell")) {
            remoteScriptArgument = remoteScriptArgument.split("'")[1];
        }
        return remoteScriptArgument;
    }
}
