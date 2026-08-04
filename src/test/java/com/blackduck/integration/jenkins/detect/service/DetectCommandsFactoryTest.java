package com.blackduck.integration.jenkins.detect.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.TaskListener;

class DetectCommandsFactoryTest {

    private static TaskListener mockedTaskListener;
    private static Launcher mockedLauncher;
    private static Node mockedNode;
    private static AbstractBuild<?, ?> mockedAbstractBuild;
    private static BuildListener mockedBuildListener;
    private static final EnvVars emptyEnvVars = new EnvVars();

    @BeforeEach
    void setup() {
        mockedTaskListener = mock(TaskListener.class);
        mockedLauncher = mock(Launcher.class);
        mockedNode = mock(Node.class);
        mockedAbstractBuild = mock(AbstractBuild.class);
        mockedBuildListener = mock(BuildListener.class);
    }

    @Test
    void testPipelineNullWorkspace() {
        AbortException exception = assertThrows(AbortException.class, () -> DetectCommandsFactory.fromPipeline(mockedTaskListener, emptyEnvVars, mockedLauncher, mockedNode, null));
        assertEquals(DetectCommandsFactory.NULL_WORKSPACE, exception.getMessage());
    }

    @Test
    void testPostBuildNullWorkspace() throws Exception {
        doReturn(emptyEnvVars).when(mockedAbstractBuild).getEnvironment(mockedBuildListener);
        doReturn(null).when(mockedAbstractBuild).getWorkspace();
        AbortException exception = assertThrows(AbortException.class, () -> DetectCommandsFactory.fromPostBuild(mockedAbstractBuild, mockedLauncher, mockedBuildListener));
        assertEquals(DetectCommandsFactory.NULL_WORKSPACE, exception.getMessage());
    }

}
