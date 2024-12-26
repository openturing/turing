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

package com.viglet.turing.connector.plugin.sprinklr.utils;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.net.URI;
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
    private URI uri;
    private float fileSize;
    private String extension;

    /**
     * Converts this FileAsset to an attribute map.
     * @return the attribute map.
     */
    public Map<String, Object> toMapAttributes() {
        var attributes = new HashMap<String, Object>();

        var formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String formatedIndexingDate = formatter.format(indexingDate);
        String formatedModificationDate = formatter.format(modificationDate);

        attributes.put("id", id);
        attributes.put("title", filename);
        attributes.put("text", ocrContent);
        attributes.put("publication_date", formatedIndexingDate);
        attributes.put("modification_date", formatedModificationDate);
        attributes.put("url", uri);
        attributes.put("filesize", fileSize);
        attributes.put("extension", extension);
        attributes.put("source_apps", List.of("SPRINKLR"));
        attributes.put("type", "Static File");


        return attributes;
    }
}
