package com.viglet.turing.artemis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.jms.client.ActiveMQObjectMessage;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.commons.exception.TurRuntimeException;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;

@Service
public class TurQueueBrowserService {

    private final MBeanServer mbeanServer;
    private final JmsTemplate jmsTemplate;
    private final ObjectNameBuilder nameBuilder;

    public TurQueueBrowserService(MBeanServer mbeanServer, JmsTemplate jmsTemplate) {
        this.mbeanServer = mbeanServer;
        this.jmsTemplate = jmsTemplate;
        this.nameBuilder = ObjectNameBuilder.DEFAULT;
    }

    public List<Map<String, Object>> listItemsInQueue(String queueName) {
        try {
            ObjectName queueObjectName = nameBuilder.getQueueObjectName(
                    SimpleString.of(queueName),
                    SimpleString.of(queueName),
                    RoutingType.ANYCAST);

            QueueControl queueControl = createProxy(queueObjectName, QueueControl.class);
            return Arrays.asList(queueControl.listMessages(""));
        } catch (Exception e) {
            throw new TurRuntimeException("Error listing items from queue: " + queueName, e);
        }
    }

    /**
     * Retrieves all queue names from the broker.
     */
    public List<String> listAllQueues() {
        try {
            // Use the builder instead of hardcoded string to avoid "org.apache.activemq..."
            // errors
            ObjectName brokerObjectName = nameBuilder.getActiveMQServerObjectName();
            ActiveMQServerControl serverControl = createProxy(brokerObjectName, ActiveMQServerControl.class);

            return Arrays.asList(serverControl.getQueueNames());
        } catch (Exception e) {
            throw new TurRuntimeException("Error listing all queues from broker.", e);
        }
    }

    /**
     * Browses queue contents specifically for TurSNJobItems document IDs.
     */
    public List<String> browseQueueContents(String queueName, int maxMessages) {
        return jmsTemplate.browse(queueName, (Session session, QueueBrowser browser) -> {
            if (browser == null)
                return Collections.emptyList();

            List<String> contents = new ArrayList<>();
            Enumeration<?> enumeration = browser.getEnumeration();

            int count = 0;
            while (enumeration.hasMoreElements() && count < maxMessages) {
                if (enumeration.nextElement() instanceof Message message) {
                    processMessage(message, contents);
                }
                count++;
            }
            return contents;
        });
    }

    private void processMessage(Message message, List<String> contents) {
        try {
            if (message instanceof ActiveMQObjectMessage activeMQMsg &&
                    activeMQMsg.getObject() instanceof TurSNJobItems jobItems) {

                jobItems.getTuringDocuments().forEach(doc -> contents.add(doc.getId()));
            } else {
                contents.add("Unsupported or empty message type: " + message.getClass().getSimpleName());
            }
        } catch (JMSException e) {
            contents.add("Error reading message: " + e.getMessage());
        }
    }

    private <T> T createProxy(ObjectName objectName, Class<T> interfaceClass) {
        return MBeanServerInvocationHandler.newProxyInstance(mbeanServer, objectName, interfaceClass, false);
    }
}