package com.viglet.turing.api.sn.queue;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.listener.MessageListenerContainer;

@ExtendWith(MockitoExtension.class)
class TurSNQueueControlServiceTest {

    @Mock
    private JmsListenerEndpointRegistry registry;

    @Mock
    private MessageListenerContainer messageListenerContainer;

    @InjectMocks
    private TurSNQueueControlService service;

    @BeforeEach
    void setUp() {
        // No setup needed - @InjectMocks automatically initializes the service with
        // mocked dependencies
    }

    @Test
    void testSuspendQueue_Running() {
        when(registry.getListenerContainerIds()).thenReturn(Collections.singleton("list1"));
        when(registry.getListenerContainer("list1")).thenReturn(messageListenerContainer);
        when(messageListenerContainer.isRunning()).thenReturn(true);

        service.suspendQueue("list1");

        verify(messageListenerContainer, times(1)).stop();
    }

    @Test
    void testSuspendQueue_NotRunning() {
        when(registry.getListenerContainerIds()).thenReturn(Collections.singleton("list1"));
        when(registry.getListenerContainer("list1")).thenReturn(messageListenerContainer);
        when(messageListenerContainer.isRunning()).thenReturn(false);

        service.suspendQueue("list1");

        verify(messageListenerContainer, never()).stop();
    }

    @Test
    void testStartQueue_NotRunning() {
        when(registry.getListenerContainer("list1")).thenReturn(messageListenerContainer);
        when(messageListenerContainer.isRunning()).thenReturn(false);

        service.startQueue("list1");

        verify(messageListenerContainer, times(1)).start();
    }

    @Test
    void testStartQueue_Running() {
        when(registry.getListenerContainer("list1")).thenReturn(messageListenerContainer);
        when(messageListenerContainer.isRunning()).thenReturn(true);

        service.startQueue("list1");

        verify(messageListenerContainer, never()).start();
    }

    @Test
    void testCheckTuring_Suspended() {
        service.startMonitoring(); // Sets suspendedQueue = true

        when(registry.getListenerContainer("indexingQueueListener")).thenReturn(messageListenerContainer);
        when(messageListenerContainer.isRunning()).thenReturn(false);

        service.checkTuring();

        verify(messageListenerContainer, times(1)).start();
    }
}
