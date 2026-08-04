package com.blackduck.integration.jenkins.detect.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DetectJenkinsExceptionTest {

    private static final String GEN_MESSAGE = "Test Message #1";
    private static final String THROWABLE_MESSAGE = "Test Message #2";
    private static final Throwable throwable = new Throwable(THROWABLE_MESSAGE);

    @Test
    void testEmptyConstructor() {
        DetectJenkinsException detectJenkinsException = new DetectJenkinsException();
        assertNull(detectJenkinsException.getMessage());
        assertNull(detectJenkinsException.getCause());
    }

    @Test
    void testNullStringConstructor() {
        DetectJenkinsException detectJenkinsException = new DetectJenkinsException((String) null);
        assertNull(detectJenkinsException.getMessage());
        assertNull(detectJenkinsException.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", GEN_MESSAGE })
    void testMessageConstructor(String message) {
        DetectJenkinsException detectJenkinsException = new DetectJenkinsException(message);
        assertEquals(message, detectJenkinsException.getMessage());
        assertNull(detectJenkinsException.getCause());
    }

    @Test
    void testThrowableConstructor() {
        DetectJenkinsException detectJenkinsException = new DetectJenkinsException(throwable);
        assertNotNull(detectJenkinsException.getMessage());
        assertEquals(THROWABLE_MESSAGE, detectJenkinsException.getCause().getMessage());
        assertEquals(throwable, detectJenkinsException.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", GEN_MESSAGE })
    void testMessageAndThrowableConstructor(String message) {
        DetectJenkinsException detectJenkinsException = new DetectJenkinsException(message, throwable);
        assertEquals(message, detectJenkinsException.getMessage());
        assertEquals(THROWABLE_MESSAGE, detectJenkinsException.getCause().getMessage());
        assertEquals(throwable, detectJenkinsException.getCause());
    }

}
