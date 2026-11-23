package com.blackduck.integration.jenkins.detect;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.blackduck.integration.jenkins.detect.exception.DetectJenkinsException;
import com.blackduck.integration.jenkins.detect.extensions.ScriptOrJarDownloadStrategy;
import com.blackduck.integration.jenkins.extensions.JenkinsIntLogger;

class DetectPipelineCommandsTest {

    private JenkinsIntLogger mockedLogger;
    private DetectRunner mockedDetectRunner;
    private static final ScriptOrJarDownloadStrategy DOWNLOAD_STRATEGY = new ScriptOrJarDownloadStrategy();

    @BeforeEach
    void setup() {
        mockedDetectRunner = mock(DetectRunner.class);
        mockedLogger = mock(JenkinsIntLogger.class);
    }

    @Test
    void testRunDetectPipelineExceptionFailure() throws Exception {
        when(mockedDetectRunner.runDetect(any(), anyString(), any(ScriptOrJarDownloadStrategy.class))).thenThrow(new IOException());

        DetectPipelineCommands detectCommands = new DetectPipelineCommands(mockedDetectRunner, mockedLogger);

        assertThrows(IOException.class, () -> detectCommands.runDetect(false, StringUtils.EMPTY, DOWNLOAD_STRATEGY));
    }

    @Test
    void testRunDetectPipelineSuccess() throws Exception {
        when(mockedDetectRunner.runDetect(any(), anyString(), any(ScriptOrJarDownloadStrategy.class))).thenReturn(0);

        DetectPipelineCommands detectCommands = new DetectPipelineCommands(mockedDetectRunner, mockedLogger);
        detectCommands.runDetect(false, StringUtils.EMPTY, DOWNLOAD_STRATEGY);

        verify(mockedLogger, never()).error(anyString());
    }

    @Test
    void testRunDetectPipelineExitCodeExceptionFailure() throws Exception {
        when(mockedDetectRunner.runDetect(any(), anyString(), any(ScriptOrJarDownloadStrategy.class))).thenReturn(1);

        DetectPipelineCommands detectCommands = new DetectPipelineCommands(mockedDetectRunner, mockedLogger);
        assertThrows(DetectJenkinsException.class, () -> detectCommands.runDetect(false, StringUtils.EMPTY, DOWNLOAD_STRATEGY));

        verify(mockedLogger, never()).error(anyString());
    }

    @Test
    void testRunDetectPipelineReturnExitCodeFailure() throws Exception {
        when(mockedDetectRunner.runDetect(any(), anyString(), any(ScriptOrJarDownloadStrategy.class))).thenReturn(1);

        DetectPipelineCommands detectCommands = new DetectPipelineCommands(mockedDetectRunner, mockedLogger);
        detectCommands.runDetect(true, StringUtils.EMPTY, DOWNLOAD_STRATEGY);

        verify(mockedLogger).error(anyString());
    }
}
