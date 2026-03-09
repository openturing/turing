package com.viglet.turing.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Enumeration;
import java.util.List;

import javax.management.MBeanServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.BrowserCallback;
import org.springframework.jms.core.JmsTemplate;

import com.viglet.turing.commons.exception.TurRuntimeException;

import jakarta.jms.Message;
import jakarta.jms.QueueBrowser;

@ExtendWith(MockitoExtension.class)
class TurQueueBrowserServiceTest {

    @Mock
    private MBeanServer mbeanServer;

    @Mock
    private JmsTemplate jmsTemplate;

    @Mock
    private QueueBrowser queueBrowser;

    @Mock
    private Enumeration<Object> enumeration;

    private TurQueueBrowserService service;

    @BeforeEach
    void setUp() {
        service = new TurQueueBrowserService(mbeanServer, jmsTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnEmptyListWhenQueueBrowserIsNull() {
        when(jmsTemplate.browse(eq("queue.test"), any(BrowserCallback.class)))
                .thenAnswer(invocation -> {
                    BrowserCallback<List<String>> callback = invocation.getArgument(1);
                    return callback.doInJms(null, null);
                });

        List<String> result = service.browseQueueContents("queue.test", 5);

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldAddUnsupportedMessageTypeEntryWhenMessageIsNotActiveMqObjectMessage() throws Exception {
        Message message = org.mockito.Mockito.mock(Message.class);
        when(queueBrowser.getEnumeration()).thenReturn(enumeration);
        when(enumeration.hasMoreElements()).thenReturn(true, false);
        when(enumeration.nextElement()).thenReturn(message);
        when(jmsTemplate.browse(eq("queue.test"), any(BrowserCallback.class)))
                .thenAnswer(invocation -> {
                    BrowserCallback<List<String>> callback = invocation.getArgument(1);
                    return callback.doInJms(null, queueBrowser);
                });

        List<String> result = service.browseQueueContents("queue.test", 5);

        assertThat(result).singleElement()
                .asString()
                .startsWith("Unsupported or empty message type:");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldRespectMaxMessagesLimitWhenBrowsingQueue() throws Exception {
        Message message = org.mockito.Mockito.mock(Message.class);
        when(queueBrowser.getEnumeration()).thenReturn(enumeration);
        when(enumeration.hasMoreElements()).thenReturn(true, true, true);
        when(enumeration.nextElement()).thenReturn(message);
        when(jmsTemplate.browse(eq("queue.test"), any(BrowserCallback.class)))
                .thenAnswer(invocation -> {
                    BrowserCallback<List<String>> callback = invocation.getArgument(1);
                    return callback.doInJms(null, queueBrowser);
                });

        List<String> result = service.browseQueueContents("queue.test", 1);

        assertThat(result).hasSize(1);
        verify(enumeration, times(1)).nextElement();
    }

    @Test
    void shouldWrapErrorsWhenListingAllQueues() {
        assertThatThrownBy(() -> service.listAllQueues())
                .isInstanceOf(TurRuntimeException.class)
                .hasMessageContaining("Error listing all queues from broker.");
    }

    @Test
    void shouldWrapErrorsWhenListingItemsInQueue() {
        assertThatThrownBy(() -> service.listItemsInQueue("queue.test"))
                .isInstanceOf(TurRuntimeException.class)
                .hasMessageContaining("Error listing items from queue: queue.test");
    }
}
