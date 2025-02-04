package com.viglet.turing.client.ocr;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TurFileAttributes {
    private String content;
    private String name;
    private String title;
    private String extension;
    private TurFileSize size = new TurFileSize();
    private Date lastModified = new Date();
    private Map<String, String> metadata = new HashMap<String, String>();

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public TurFileSize getSize() {
        return size;
    }

    public void setSize(TurFileSize size) {
        this.size = size;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}