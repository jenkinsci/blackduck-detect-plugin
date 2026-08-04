package com.blackduck.integration.jenkins.detect;

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

import com.blackduck.integration.exception.IntegrationException;
import com.blackduck.integration.jenkins.detect.extensions.ScriptOrJarDownloadStrategy;
import com.blackduck.integration.jenkins.service.JenkinsBuildService;

class DetectFreestyleCommandsTest {

    private JenkinsBuildService mockedBuildService;
    private DetectRunner mockedDetectRunner;
    private static final ScriptOrJarDownloadStrategy DOWNLOAD_STRATEGY = new ScriptOrJarDownloadStrategy();

    @BeforeEach
    void setup() {
        mockedDetectRunner = mock(DetectRunner.class);
        mockedBuildService = mock(JenkinsBuildService.class);
    }

    @Test
    void testRunDetectSuccess() throws Exception {
        when(mockedDetectRunner.runDetect(any(), anyString(), any(ScriptOrJarDownloadStrategy.class))).thenReturn(0);

        DetectFreestyleCommands detectCommands = new DetectFreestyleCommands(mockedBuildService, mockedDetectRunner);
        detectCommands.runDetect(StringUtils.EMPTY, DOWNLOAD_STRATEGY);

        verify(mockedBuildService, never()).markBuildAborted();
        verify(mockedBuildService, never()).markBuildInterrupted();
        verify(mockedBuildService, never()).markBuildUnstable(any());
        verify(mockedBuildService, never()).markBuildFailed(any(String.class));
        verify(mockedBuildService, never()).markBuildFailed(any(Exception.class));
    }

    @Test
    void testRunDetectExitCodeFailure() throws Exception {
        when(mockedDetectRunner.runDetect(any(), anyString(), any(ScriptOrJarDownloadStrategy.class))).thenReturn(1);

        DetectFreestyleCommands detectCommands = new DetectFreestyleCommands(mockedBuildService, mockedDetectRunner);
        detectCommands.runDetect(StringUtils.EMPTY, DOWNLOAD_STRATEGY);

        verify(mockedBuildService).markBuildFailed(any(String.class));

        verify(mockedBuildService, never()).markBuildAborted();
        verify(mockedBuildService, never()).markBuildInterrupted();
        verify(mockedBuildService, never()).markBuildUnstable(any());
        verify(mockedBuildService, never()).markBuildFailed(any(Exception.class));
    }

    @Test
    void testRunDetectIntegrationFailure() throws Exception {
        when(mockedDetectRunner.runDetect(any(), anyString(), any(ScriptOrJarDownloadStrategy.class))).thenThrow(new IntegrationException());

        DetectFreestyleCommands detectCommands = new DetectFreestyleCommands(mockedBuildService, mockedDetectRunner);
        detectCommands.runDetect(StringUtils.EMPTY, DOWNLOAD_STRATEGY);

        verify(mockedBuildService).markBuildFailed(any(IntegrationException.class));

        verify(mockedBuildService, never()).markBuildAborted();
        verify(mockedBuildService, never()).markBuildInterrupted();
        verify(mockedBuildService, never()).markBuildUnstable(any());
        verify(mockedBuildService, never()).markBuildFailed(any(String.class));
    }

    @Test
    void testRunDetectInterrupted() throws Exception {
        when(mockedDetectRunner.runDetect(any(), anyString(), any(ScriptOrJarDownloadStrategy.class))).thenThrow(new InterruptedException());

        DetectFreestyleCommands detectCommands = new DetectFreestyleCommands(mockedBuildService, mockedDetectRunner);
        detectCommands.runDetect(StringUtils.EMPTY, DOWNLOAD_STRATEGY);

        verify(mockedBuildService).markBuildInterrupted();

        verify(mockedBuildService, never()).markBuildAborted();
        verify(mockedBuildService, never()).markBuildUnstable(any());
        verify(mockedBuildService, never()).markBuildFailed(any(String.class));
        verify(mockedBuildService, never()).markBuildFailed(any(Exception.class));
    }

    @Test
    void testRunDetectExceptionFailure() throws Exception {
        when(mockedDetectRunner.runDetect(any(), anyString(), any(ScriptOrJarDownloadStrategy.class))).thenThrow(new IOException());

        DetectFreestyleCommands detectCommands = new DetectFreestyleCommands(mockedBuildService, mockedDetectRunner);
        detectCommands.runDetect(StringUtils.EMPTY, DOWNLOAD_STRATEGY);

        verify(mockedBuildService).markBuildFailed(any(IOException.class));

        verify(mockedBuildService, never()).markBuildAborted();
        verify(mockedBuildService, never()).markBuildInterrupted();
        verify(mockedBuildService, never()).markBuildUnstable(any());
        verify(mockedBuildService, never()).markBuildFailed(any(String.class));
    }

}
