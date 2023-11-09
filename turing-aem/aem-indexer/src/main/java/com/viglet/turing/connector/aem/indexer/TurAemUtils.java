package com.viglet.turing.connector.aem.indexer;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Slf4j
public class TurAemUtils {
    public static boolean hasProperty(JSONObject jsonObject, String property) {
        return jsonObject.has(property) && jsonObject.get(property) != null;
    }
    public static String getPropertyValue(Object property) {
        try {
            if (property instanceof JSONArray) {
                JSONArray propertyArray = (JSONArray) property;
                return !propertyArray.isEmpty() ? propertyArray.get(0).toString() : "";
            }
            else
                return property.toString();
        } catch (IllegalStateException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
    public static String getJcrPropertyValue(Node node, String propertyName)
            throws RepositoryException {
        if (node.hasProperty(propertyName))
            return getPropertyValue(node.getProperty(propertyName));
        return null;
    }

    public static void getNode(Node node, StringBuffer components) throws RepositoryException, ValueFormatException {

        if (node.hasNodes() && (node.getPath().startsWith("/content") || node.getPath().equals("/"))) {
            NodeIterator nodeIterator = node.getNodes();
            while (nodeIterator.hasNext()) {

                Node nodeChild = nodeIterator.nextNode();
                if (nodeChild.hasProperty("jcr:title"))
                    components.append(TurAemUtils.getJcrPropertyValue(nodeChild, "jcr:title"));
                if (nodeChild.hasProperty("text"))
                    components.append(TurAemUtils.getJcrPropertyValue(nodeChild, "text"));
                if (nodeChild.hasNodes()) {
                    getNode(nodeChild, components);
                }
            }
        }
    }
}
