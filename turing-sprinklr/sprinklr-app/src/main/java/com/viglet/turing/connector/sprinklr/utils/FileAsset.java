package com.viglet.turing.connector.sprinklr.utils;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a File Asset extracted from Sprinklr Knowledge Base search result.
 * @author Gabriel F. Gomazako
 * @since 0.3.9
 */
@AllArgsConstructor
@NoArgsConstructor
public class FileAsset {
    private String id;
    private String filename;
    private String ocrContent;
    private Date indexingDate;
    private Date modificationDate;
    private URL url;
    private float fileSize;
    private String extension;

    /**
     * Converts this FileAsset to an attribute map.
     * @return the attribute map.
     */
    public Map<String, Object> toMapAttributes() {
        var attributes = new HashMap<String, Object>();

        var formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

        // Value for date if indexingDate or modificationDate is null;
        String formatedIndexingDate = "2000-01-01 00:00:00";
        String formatedModificationDate = "2000-01-01 00:00:00";
        if(indexingDate != null) {
            formatedIndexingDate = formatter.format(indexingDate);
        }
        if(modificationDate != null){
            formatedModificationDate = formatter.format(modificationDate);
        }

        attributes.put("id", id);
        attributes.put("title", filename);
        attributes.put("text", ocrContent);
        attributes.put("publication_date", formatedIndexingDate);
        attributes.put("modification_date", formatedModificationDate);
        attributes.put("url", url);
        attributes.put("filesize", fileSize);
        attributes.put("extension", extension);
        attributes.put("source_apps", List.of("SPRINKLR"));
        attributes.put("type", "Static File");

        return attributes;
    }
}
