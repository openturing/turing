/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector.aem.commons;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.viglet.turing.connector.aem.commons.TurAemConstants.*;
import static com.viglet.turing.connector.aem.commons.TurAemConstants.JCR_CONTENT;
import static java.time.ZoneOffset.UTC;
import static org.apache.jackrabbit.JcrConstants.*;

@Getter
@Slf4j
public class TurAemObject {
    private Calendar lastModified;
    private Calendar createdDate;
    private Calendar publicationDate;
    private boolean contentFragment = false;
    private boolean delivered = false;
    private final String type;
    private final String path;
    private final String url;
    private final JSONObject jcrNode;
    private JSONObject jcrContentNode = new JSONObject();
    private String title;
    private String template;
    private String model;
    private final Map<String, Object> attributes = new HashMap<>();
    public final SimpleDateFormat aemJsonDateFormat = new SimpleDateFormat(DATE_JSON_FORMAT, Locale.ENGLISH);

    public TurAemObject(String nodePath, JSONObject jcrNode) {
        this.jcrNode = jcrNode;
        this.path = nodePath;
        this.url = nodePath + HTML;
        this.type = jcrNode.has(JCR_PRIMARYTYPE) ? jcrNode.getString(JCR_PRIMARYTYPE) : EMPTY_VALUE;
        try {
            if (jcrNode.has(JCR_CONTENT)) {
                processJcrContent(jcrNode);
            }
            if (jcrNode.has(JCR_CREATED)) {
                processJcrCreated(jcrNode);
            }
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void processJcrCreated(JSONObject jcrNode) throws ParseException {
        Calendar createdDateCalendar = Calendar.getInstance();
        createdDateCalendar.setTime(aemJsonDateFormat.parse(jcrNode.getString(JCR_CREATED)));
        this.createdDate = createdDateCalendar;
    }

    private void processJcrContent(JSONObject jcrNode) throws ParseException {
        this.jcrContentNode = jcrNode.getJSONObject(JCR_CONTENT);
        this.template = getJcrTemplate();
        this.delivered = getJcrDelivered();
        this.title = getJcrTitle();
        this.contentFragment = isJcrContentFragment();
        getDataFolder(jcrContentNode);
        this.lastModified = getJcrLastModified();
        this.publicationDate = getJcrPublicationDate();
    }

    private boolean isJcrContentFragment() {
        return TurAemCommonsUtils.hasProperty(this.jcrContentNode, CONTENT_FRAGMENT) ?
                this.jcrContentNode.getBoolean(CONTENT_FRAGMENT) :
                (this.contentFragment = false);
    }

    private boolean getJcrDelivered() {
        return isActivated(CQ_LAST_REPLICATION_ACTION)
                && isActivated(CQ_LAST_REPLICATION_ACTION_PUBLISH);
    }

    private String getJcrTemplate() {
        return jcrContentNode.has(CQ_TEMPLATE) ? this.jcrContentNode.getString(CQ_TEMPLATE) : EMPTY_VALUE;
    }

    private String getJcrTitle() {
        return jcrContentNode.has(JCR_TITLE) ? this.jcrContentNode.getString(JCR_TITLE) : EMPTY_VALUE;
    }

    private Calendar getJcrPublicationDate() throws ParseException {
        return getCalendar(CQ_LAST_REPLICATED_PUBLISH, CQ_LAST_REPLICATED);
    }

    private Calendar getCalendar(String cqLastReplicatedPublish, String cqLastReplicated) throws ParseException {
        Calendar calendar = Calendar.getInstance();
        if (this.jcrContentNode.has(cqLastReplicatedPublish)) {
            calendar.setTime(aemJsonDateFormat.parse(this.jcrContentNode.getString(cqLastReplicatedPublish)));
        } else if (this.jcrContentNode.has(cqLastReplicated)) {
            calendar.setTime(aemJsonDateFormat.parse(this.jcrContentNode.getString(cqLastReplicated)));
        }
        return calendar;
    }

    private Calendar getJcrLastModified() throws ParseException {
        return getCalendar(JCR_LASTMODIFIED, CQ_LAST_MODIFIED);
    }

    private boolean isActivated(String attribute) {
        return jcrContentNode.has(attribute) && this.jcrContentNode.getString(attribute).equals(ACTIVATE);
    }

    private void getDataFolder(JSONObject jcrContentNode) {
        if (TurAemCommonsUtils.hasProperty(jcrContentNode, DATA_FOLDER)) {
            JSONObject jcrDataRootNode = jcrContentNode.getJSONObject(DATA_FOLDER);
            if (TurAemCommonsUtils.hasProperty(jcrDataRootNode, CQ_MODEL)) {
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
