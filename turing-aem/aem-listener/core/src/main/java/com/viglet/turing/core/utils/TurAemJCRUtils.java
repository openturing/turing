package com.viglet.turing.core.utils;


import lombok.extern.slf4j.Slf4j;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class TurAemJCRUtils {

    private TurAemJCRUtils() {
    }

    public static final String LOG_MESSAGE = "Error in some JCRUtils Method. {}";

    public static Property getProperty(Node node, String propertyName) {
        try {
            if (node.hasProperty(propertyName))
                return node.getProperty(propertyName);
        } catch (RepositoryException repositoryException) {
            log.error(LOG_MESSAGE, repositoryException.toString());
        }
        return null;
    }

    public static String getValue(Property property) {
        try {
            return property.getValue().getString();
        } catch (RepositoryException repositoryException) {
            log.error(LOG_MESSAGE, repositoryException.toString());
        }
        return null;
    }

    public static String getValue(Node node, String attributeName) {
        try {
            if (node != null && node.hasProperty(attributeName)) {
                Property property = node.getProperty(attributeName);
                if (property != null)
                    return property.getValue().getString().trim();
            }
        } catch (IllegalStateException | RepositoryException e) {
            log.error(LOG_MESSAGE, e.getMessage());
        }
        return "";
    }

    public static Node getSpecificNode(Node node, String nodeName) {
        try {
            return node.getNode(nodeName);
        } catch (RepositoryException e) {
            log.error(LOG_MESSAGE, e.getMessage());
        }
        return node;
    }

    public static List<String> getValues(Node node, String attributeName) {
        List<String> values = new ArrayList<>();

        try {
            if (node != null && node.hasProperty(attributeName)) {
                Property property = node.getProperty(attributeName);
                if (property != null) {
                    if (property.isMultiple()) {
                        for (Value value : property.getValues())
                            values.add(value.getString());
                    } else {
                        values.add(property.getValue().getString());
                    }
                }
            }
        } catch (RepositoryException e) {
            log.error(LOG_MESSAGE, e.getMessage());
        }

        return values;
    }

    public static String getContent(Node node, String property) {
        return Optional.of(node)
                .map(n -> TurAemJCRUtils.getProperty(n, property))
                .map(TurAemJCRUtils::getValue).orElse("");
    }
}
