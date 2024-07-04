package com.viglet.turing.nutch.indexwriter;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.util.ObjectCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class TurMappingReader {
    private static final Logger logger = LoggerFactory.getLogger(TurMappingReader.class);
    private static final String SOURCE_ATTRIBUTE = "source";
    private static final String DESTINATION_ATTRIBUTE = "dest";
    private static final String URL_ATTRIBUTE = "url";
    private static final String SN_SITE_ATTRIBUTE = "snSite";
    private static final String SITE_TAG = "site";
    private static final String UNIQUE_KEY_TAG = "uniqueKey";
    private static final String COPY_FIELD_TAG = "copyField";
    private static final String FIELD_TAG = "field";
    private static final String TURING_MAPPING_CONFIGURATION = "turing.mapping.file";
    private static final String TURING_MAPPING_FILE =  "turing-mapping.xml";
    private final Configuration conf;

    private final Map<String, String> keyMap = new HashMap<>();

    private final Map<String, String> copyMap = new HashMap<>();

    private final Map<String, String> siteMap = new HashMap<>();

    public static synchronized TurMappingReader getInstance(Configuration conf) {
        ObjectCache cache = ObjectCache.get(conf);
        TurMappingReader instance = (TurMappingReader) cache.getObject(TurMappingReader.class.getName());
        if (instance == null) {
            instance = new TurMappingReader(conf);
            cache.setObject(TurMappingReader.class.getName(), instance);
        }
        return instance;
    }

    protected TurMappingReader(Configuration conf) {
        this.conf = conf;
        parseMapping();
    }

    private void parseMapping() {
        InputStream ssInputStream = this.conf.getConfResourceAsInputStream(
                this.conf.get(TURING_MAPPING_CONFIGURATION, TURING_MAPPING_FILE));
        InputSource inputSource = new InputSource(ssInputStream);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputSource);
            Element rootElement = document.getDocumentElement();
            NodeList fieldList = rootElement.getElementsByTagName(FIELD_TAG);

            if (fieldList.getLength() > 0)
                for (int i = 0; i < fieldList.getLength(); i++) {
                    Element element = (Element) fieldList.item(i);
                    sourceDestLog("source: {} dest: {}", element, SOURCE_ATTRIBUTE, DESTINATION_ATTRIBUTE);
                    this.keyMap.put(element.getAttribute(SOURCE_ATTRIBUTE), element.getAttribute(DESTINATION_ATTRIBUTE));
                }
            NodeList copyFieldList = rootElement.getElementsByTagName(COPY_FIELD_TAG);
            if (copyFieldList.getLength() > 0)
                for (int i = 0; i < copyFieldList.getLength(); i++) {
                    Element element = (Element) copyFieldList.item(i);
                    sourceDestLog("source: {} dest: {}", element, SOURCE_ATTRIBUTE, DESTINATION_ATTRIBUTE);
                    this.copyMap.put(element.getAttribute(SOURCE_ATTRIBUTE), element.getAttribute(DESTINATION_ATTRIBUTE));
                }
            NodeList uniqueKeyItem = rootElement.getElementsByTagName(UNIQUE_KEY_TAG);
            if (uniqueKeyItem.getLength() > 1) {
                logger.warn("More than one unique key definitions found in solr index mapping, using default 'id'");
            } else if (uniqueKeyItem.getLength() == 0) {
                logger.warn("No unique key definition found in solr index mapping using, default 'id'");
            }
            NodeList siteList = rootElement.getElementsByTagName(SITE_TAG);
            if (siteList.getLength() > 0)
                for (int i = 0; i < siteList.getLength(); i++) {
                    Element element = (Element) siteList.item(i);
                    sourceDestLog("url: {} snSite: {}", element, URL_ATTRIBUTE, SN_SITE_ATTRIBUTE);
                    this.siteMap.put(element.getAttribute(URL_ATTRIBUTE), element.getAttribute(SN_SITE_ATTRIBUTE));
                }
        } catch (SAXException | IOException | ParserConfigurationException e) {
            logger.warn(e.toString());
        }
    }

    private static void sourceDestLog(String s, Element element, String sourceAttribute, String destinationAttribute) {
        if (logger.isInfoEnabled()) {
            logger.info(s,
                    element.getAttribute(sourceAttribute), element.getAttribute(destinationAttribute));
        }
    }

    public String hasCopy(String key) {
        if (this.copyMap.containsKey(key))
            key = this.copyMap.get(key);
        return key;
    }

    public String getSNSite(String url) {
        for (Map.Entry<String, String> siteURL : this.siteMap.entrySet()) {
            if (url.startsWith(siteURL.getKey())) {
                return siteURL.getValue();
            }
        }
        return null;
    }

    public String mapKey(String key) {
        if (this.keyMap.containsKey(key))
            key = this.keyMap.get(key);
        return key;
    }

    public String mapCopyKey(String key) {
        return hasCopy(key);
    }
}
