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
    public static Logger LOG = LoggerFactory.getLogger(TurMappingReader.class);

    private Configuration conf;

    private Map<String, String> keyMap = new HashMap<>();

    private Map<String, String> copyMap = new HashMap<>();

    private Map<String, String> siteMap = new HashMap<>();

    private String uniqueKey = "id";

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
        InputStream ssInputStream = null;
        ssInputStream = this.conf.getConfResourceAsInputStream(this.conf.get("turing.mapping.file", "turing-mapping.xml"));
        InputSource inputSource = new InputSource(ssInputStream);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputSource);
            Element rootElement = document.getDocumentElement();
            NodeList fieldList = rootElement.getElementsByTagName("field");

            if (fieldList.getLength() > 0)
                for (int i = 0; i < fieldList.getLength(); i++) {
                    Element element = (Element) fieldList.item(i);
                    LOG.info("source: " + element.getAttribute("source") + " dest: " + element.getAttribute("dest"));
                    this.keyMap.put(element.getAttribute("source"), element.getAttribute("dest"));
                }
            NodeList copyFieldList = rootElement.getElementsByTagName("copyField");
            if (copyFieldList.getLength() > 0)
                for (int i = 0; i < copyFieldList.getLength(); i++) {
                    Element element = (Element) copyFieldList.item(i);
                    LOG.info("source: " + element.getAttribute("source") + " dest: " + element.getAttribute("dest"));
                    this.copyMap.put(element.getAttribute("source"), element.getAttribute("dest"));
                }
            NodeList uniqueKeyItem = rootElement.getElementsByTagName("uniqueKey");
            if (uniqueKeyItem.getLength() > 1) {
                LOG.warn("More than one unique key definitions found in solr index mapping, using default 'id'");
                this.uniqueKey = "id";
            } else if (uniqueKeyItem.getLength() == 0) {
                LOG.warn("No unique key definition found in solr index mapping using, default 'id'");
            } else {
                this.uniqueKey = uniqueKeyItem.item(0).getFirstChild().getNodeValue();
            }
            NodeList siteList = rootElement.getElementsByTagName("site");
            if (siteList.getLength() > 0)
                for (int i = 0; i < siteList.getLength(); i++) {
                    Element element = (Element) siteList.item(i);
                    LOG.info("url: " + element.getAttribute("url") + " snSite: " + element.getAttribute("snSite"));
                    this.siteMap.put(element.getAttribute("url"), element.getAttribute("snSite"));
                }
        } catch (SAXException | IOException | ParserConfigurationException e) {
            LOG.warn(e.toString());
        }
    }

    public Map<String, String> getKeyMap() {
        return this.keyMap;
    }

    public Map<String, String> getCopyMap() {
        return this.copyMap;
    }

    public String getUniqueKey() {
        return this.uniqueKey;
    }

    public Map<String, String> getSiteMap() {
        return this.siteMap;
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
        if (this.copyMap.containsKey(key))
            key = this.copyMap.get(key);
        return key;
    }

    public String mapSiteKey(String key) {
        if (this.siteMap.containsKey(key))
            key = this.siteMap.get(key);
        return key;
    }
}
