package com.viglet.turing.connector.aem.commons;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

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
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    public static final String HTML = ".html";
    public static final String ACTIVATE = "Activate";
    public static final String CQ_LAST_REPLICATED_PUBLISH = "cq:lastReplicated_publish";
    public static final String CQ_LAST_REPLICATED = "cq:lastReplicated";
    private Calendar lastModified;
    private Calendar createdDate;
    private Calendar publicationDate;
    private boolean contentFragment = false;
    private boolean delivered = false;
    private final String type;
    private final String path;
    private final String url;
    private String model;
    private final JSONObject jcrNode;
    private JSONObject jcrContentNode = new JSONObject();
    private String title;
    private final Map<String, Object> attributes = new HashMap<>();

    public static final String CONTENT_FRAGMENT = "contentFragment";
    public static final String CQ_LAST_REPLICATION_ACTION = "cq:lastReplicationAction";
    public static final String CQ_LAST_REPLICATION_ACTION_PUBLISH = "cq:lastReplicationAction_publish";
    public static final String CQ_LAST_MODIFIED = "cq:lastModified";
    public static final String CQ_MODEL = "cq:model";
    public static final String DATA_FOLDER = "data";
    public static final String DATE_JSON_FORMAT = "E MMM dd yyyy HH:mm:ss 'GMT'Z";
    public static final String EMPTY_VALUE = "";
    public final SimpleDateFormat aemJsonDateFormat = new SimpleDateFormat(DATE_JSON_FORMAT, Locale.ENGLISH);

    public AemObject(String nodePath, JSONObject jcrNode) {
        this.jcrNode = jcrNode;
        this.path = nodePath;
        this.url = nodePath + HTML;
        this.type = jcrNode.has(JCR_PRIMARYTYPE) ? jcrNode.getString(JCR_PRIMARYTYPE) : EMPTY_VALUE;
        try {
            if (jcrNode.has(JCR_CONTENT)) {
                this.jcrContentNode = jcrNode.getJSONObject(JCR_CONTENT);
                this.delivered = isActivated(CQ_LAST_REPLICATION_ACTION)
                        && isActivated(CQ_LAST_REPLICATION_ACTION_PUBLISH);
                this.title = jcrContentNode.has(JCR_TITLE) ? this.jcrContentNode.getString(JCR_TITLE) : EMPTY_VALUE;
                if (TurAEMCommonsUtils.hasProperty(this.jcrContentNode, CONTENT_FRAGMENT)) {
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
                Calendar publicationDateCalendar = Calendar.getInstance();
                if (this.jcrContentNode.has(CQ_LAST_REPLICATED_PUBLISH)) {
                    publicationDateCalendar.setTime(aemJsonDateFormat.parse(this.jcrContentNode.getString(CQ_LAST_REPLICATED_PUBLISH)));
                    this.publicationDate = publicationDateCalendar;
                } else if (this.jcrContentNode.has(CQ_LAST_REPLICATED)) {
                    lastModifiedCalendar.setTime(aemJsonDateFormat.parse(this.jcrContentNode.getString(CQ_LAST_REPLICATED)));
                    this.publicationDate = publicationDateCalendar;
                }
            }
            if (jcrNode.has(JCR_CREATED)) {
                Calendar createdDateCalendar = Calendar.getInstance();
                createdDateCalendar.setTime(aemJsonDateFormat.parse(jcrNode.getString(JCR_CREATED)));
                this.createdDate = createdDateCalendar;
            }
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
        }
    }

    private boolean isActivated(String attribute) {
        return jcrContentNode.has(attribute) && this.jcrContentNode.getString(attribute).equals(ACTIVATE);
    }

    private void getDataFolder(JSONObject jcrContentNode) {
        if (TurAEMCommonsUtils.hasProperty(jcrContentNode, DATA_FOLDER)) {
            JSONObject jcrDataRootNode = jcrContentNode.getJSONObject(DATA_FOLDER);
            if (TurAEMCommonsUtils.hasProperty(jcrDataRootNode, CQ_MODEL)) {
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
            dataJson.keySet().stream().filter(key -> !key.endsWith("@LastModified")).forEach(key -> {
                Object value = finalDataJson.get(key);
                if (isDate(value.toString())) {
                    try {
                        TimeZone tz = TimeZone.getTimeZone(UTC);
                        DateFormat turingDateFormat = new SimpleDateFormat(DATE_FORMAT);
                        turingDateFormat.setTimeZone(tz);
                        this.attributes.put(key, turingDateFormat
                                .format(aemJsonDateFormat.parse(value.toString()).getTime()));
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
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "AemObject{" +
                "lastModified=" + lastModified +
                ", createdDate=" + createdDate +
                ", contentFragment=" + contentFragment +
                ", delivered=" + delivered +
                ", type='" + type + '\'' +
                ", path='" + path + '\'' +
                ", url='" + url + '\'' +
                ", model='" + model + '\'' +
                ", jcrNode=" + jcrNode +
                ", jcrContentNode=" + jcrContentNode +
                ", title='" + title + '\'' +
                ", attributes=" + attributes +
                '}';
    }
}
