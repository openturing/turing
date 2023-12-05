package com.viglet.turing.connector.aem.indexer;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.time.ZoneOffset.UTC;
import static org.apache.jackrabbit.JcrConstants.*;

@Getter
@Slf4j
public class AemObject {
    public static final String JCR_TITLE = "jcr:title";
    private Calendar lastModified;
    private Calendar createdDate;
    private boolean contentFragment = false;
    private boolean delivered = false;
    private String type;
    private String path;
    private String url;
    private String model;
    private JSONObject node;
    private JSONObject jcrContentNode;
    private String title;
    private final Map<String, Object> attributes = new HashMap<>();

    public static final String CONTENT_FRAGMENT = "contentFragment";
    public static final String CQ_IS_DELIVERED = "cq:isDelivered";
    public static final String CQ_LAST_MODIFIED = "cq:lastModified";
    public static final String CQ_MODEL = "cq:model";
    public static final String DATA_FOLDER = "data";
    public static final String DATE_JSON_FORMAT = "E MMM dd yyyy HH:mm:ss 'GMT'Z";
    public static final String EMPTY_VALUE = "";
    public static final SimpleDateFormat aemJsonDateFormat = new SimpleDateFormat(DATE_JSON_FORMAT, Locale.ENGLISH);

    public AemObject(String nodePath, JSONObject jcrNode) {
        this.node = jcrNode;
        this.path = nodePath;
        this.url = nodePath + ".html";
        this.type = jcrNode.has(JCR_PRIMARYTYPE) ? jcrNode.getString(JCR_PRIMARYTYPE) : EMPTY_VALUE;
        try {
            if (jcrNode.has(JCR_CONTENT)) {
                this.jcrContentNode = jcrNode.getJSONObject(JCR_CONTENT);
                this.delivered = jcrContentNode.has(CQ_IS_DELIVERED) && this.jcrContentNode.getBoolean(CQ_IS_DELIVERED);
                this.title = jcrContentNode.has(JCR_TITLE) ? this.jcrContentNode.getString(JCR_TITLE) : EMPTY_VALUE;
                if (TurAemUtils.hasProperty(this.jcrContentNode, CONTENT_FRAGMENT)) {
                    this.contentFragment = this.jcrContentNode.getBoolean(CONTENT_FRAGMENT);
                }
                getDataFolder(jcrContentNode);

                Calendar lastModifiedCalendar = Calendar.getInstance();
                if (this.jcrContentNode.has(JCR_LASTMODIFIED)) {
                    lastModifiedCalendar.setTime(aemJsonDateFormat.parse(this.jcrContentNode.getString(JCR_LASTMODIFIED)));
                    this.lastModified = lastModifiedCalendar;
                } else if (this.jcrContentNode.has(CQ_LAST_MODIFIED)) {
                    lastModifiedCalendar.setTime(aemJsonDateFormat.parse(this.jcrContentNode.getString(CQ_LAST_MODIFIED)));
                    this.lastModified = lastModifiedCalendar;
                }

            }
            Calendar createdDateCalendar = Calendar.getInstance();
            if (jcrNode.has(JCR_CREATED)) {

                createdDateCalendar.setTime(aemJsonDateFormat.parse(jcrNode.getString(JCR_CREATED)));
                this.createdDate = createdDateCalendar;
            }
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
        }
    }
    public AemObject(Node node) {
        try {
            this.node = new JSONObject(new Gson().toJson(node));
            this.path = node.getPath();
            this.url = node.getPath() + ".html";
            this.type = node.getProperty(JCR_PRIMARYTYPE).getString();
            this.jcrContentNode = new JSONObject(new Gson().toJson(node.getNode(JCR_CONTENT)));
            this.delivered = jcrContentNode.has(CQ_IS_DELIVERED) && this.jcrContentNode.getBoolean(CQ_IS_DELIVERED);
            this.title = jcrContentNode.has(JCR_TITLE) ? this.jcrContentNode.getString(JCR_TITLE) : EMPTY_VALUE;
            if (TurAemUtils.hasProperty(this.jcrContentNode, CONTENT_FRAGMENT)) {
                this.contentFragment = this.jcrContentNode.getBoolean(CONTENT_FRAGMENT);
            }
            getDataFolder(this.jcrContentNode);
            if (TurAemUtils.hasProperty(this.jcrContentNode, CQ_LAST_MODIFIED)) {
                this.lastModified = (Calendar) this.jcrContentNode.get(CQ_LAST_MODIFIED);
            } else if (TurAemUtils.hasProperty(this.jcrContentNode, JCR_LASTMODIFIED)) {
                this.lastModified = (Calendar) this.jcrContentNode.get(JCR_LASTMODIFIED);
            }
            if (TurAemUtils.hasProperty(this.node, JCR_CREATED)) {
                this.createdDate = node.getProperty(JCR_CREATED).getDate();
            }
        } catch (RepositoryException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void getDataFolder(JSONObject jcrContentNode) {
        if (TurAemUtils.hasProperty(jcrContentNode, DATA_FOLDER)) {
            JSONObject jcrDataRootNode = jcrContentNode.getJSONObject(DATA_FOLDER);
            if (TurAemUtils.hasProperty(jcrDataRootNode, CQ_MODEL)) {
                this.model = jcrDataRootNode.getString(CQ_MODEL);
            }
        }
    }

    public void setDataPath(String dataPath) {
        if (dataPath != null) {
            JSONObject dataJson = this.jcrContentNode;
            for (String node : dataPath.split("/")) {
                if (dataJson.has(node)) {
                    dataJson = dataJson.getJSONObject(node);
                } else {
                    return;
                }
            }
            JSONObject finalDataJson = dataJson;
            dataJson.keySet().forEach(key -> {
                Object value = finalDataJson.get(key);
                if (isDate(value.toString())) {
                    try {
                        TimeZone tz = TimeZone.getTimeZone(UTC);
                        DateFormat turingDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                        turingDateFormat.setTimeZone(tz);
                        this.attributes.put(key, turingDateFormat.format(aemJsonDateFormat.parse(value.toString()).getTime()));
                    } catch (ParseException e) {
                        log.error(e.getMessage(), e);
                    }
                } else {
                    this.attributes.put(key, value);
                }
            });
        }
    }

    public boolean isDate(String dateStr) {
        try {
            aemJsonDateFormat.parse(dateStr);
        } catch (ParseException e) {
            return false;
        }
        return true;
    }
}
