package com.blackduck.integration.jenkins.detect.service.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;
import com.blackduck.integration.jenkins.wrapper.JenkinsProxyHelper;
import com.blackduck.integration.util.OperatingSystemType;

import hudson.model.TaskListener;

class DetectScriptStrategyTest {

    private final String unescapedSpecialCharacters = "|&;<>()$`\\\"' \t\r\n\n*?[#~=%,";
    private JenkinsIntLogger defaultLogger;
    private JenkinsProxyHelper defaultProxyHelper;
    private ByteArrayOutputStream logs;

    @BeforeEach
    void setup() {
        logs = new ByteArrayOutputStream();
        TaskListener mockedTaskListener = mock(TaskListener.class);
        when(mockedTaskListener.getLogger()).thenReturn(new PrintStream(logs));
        defaultLogger = JenkinsIntLogger.logToListener(mockedTaskListener);
        defaultProxyHelper = new JenkinsProxyHelper();
    }

    @Test
    void testNoProxyDeterminable() throws Exception {
        String expectedExceptionMessage = "expected test message";

        JenkinsProxyHelper mockedProxyHelper = mock(JenkinsProxyHelper.class);
        when(mockedProxyHelper.getProxyInfo(anyString())).thenThrow(new IllegalArgumentException(expectedExceptionMessage));
        DetectScriptStrategy detectScriptStrategy = new DetectScriptStrategy(defaultLogger, mockedProxyHelper, OperatingSystemType.LINUX, null);

        detectScriptStrategy.getSetupCallable();

        assertTrue(logs.toString().contains(expectedExceptionMessage));
    }

    @Test
    void testArgumentEscaperLinux() {
        DetectScriptStrategy detectScriptStrategy = new DetectScriptStrategy(defaultLogger, defaultProxyHelper, OperatingSystemType.LINUX, null);
        String expectedEscapedString = "\\|\\&\\;\\<\\>\\(\\)\\$\\`\\\\\\\"\\'\\ \\\t\\*\\?\\[\\#\\~\\=\\%,";

        String escapedString = detectScriptStrategy.getArgumentEscaper().apply(unescapedSpecialCharacters);

        assertEquals(expectedEscapedString, escapedString);
    }

    @Test
    void testArgumentEscaperMac() {
        DetectScriptStrategy detectScriptStrategy = new DetectScriptStrategy(defaultLogger, defaultProxyHelper, OperatingSystemType.MAC, null);
        String expectedEscapedString = "\\|\\&\\;\\<\\>\\(\\)\\$\\`\\\\\\\"\\'\\ \\\t\\*\\?\\[\\#\\~\\=\\%,";

        String escapedString = detectScriptStrategy.getArgumentEscaper().apply(unescapedSpecialCharacters);

        assertEquals(expectedEscapedString, escapedString);
    }

    @Test
    void testArgumentEscaperWindows() {
        DetectScriptStrategy detectScriptStrategy = new DetectScriptStrategy(defaultLogger, defaultProxyHelper, OperatingSystemType.WINDOWS, null);
        String expectedEscapedString = "`|`&`;`<`>`(`)`$```\\`\"`'` `\t`*`?`[`#`~`=`%`,";

        String escapedString = detectScriptStrategy.getArgumentEscaper().apply(unescapedSpecialCharacters);

        assertEquals(expectedEscapedString, escapedString);
    }

}
