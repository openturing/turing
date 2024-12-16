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

package com.viglet.turing.commons.sn.field;

/**
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
public class TurSNFieldName {
    private TurSNFieldName() {
        throw new IllegalStateException("Semantic Navigation Field Names class");
    }
    public static final String ID = "id";
    public static final String URL = "url";
    public static final String ABSTRACT = "abstract";
    public static final String TEXT = "text";
    public static final String TITLE = "title";
    public static final String PUBLICATION_DATE = "publication_date";
    public static final String MODIFICATION_DATE = "modification_date";
    public static final String AUTHOR = "author";
    public static final String SECTION = "section";
    public static final String SOURCE_APPS = "source_apps";
    public static final String IMAGE = "image";
    public static final String TYPE = "type";
    public static final String SITE = "site";
    public static final String DEFAULT = "_text_";
    public static final String EXACT_MATCH = "exact_match";

}
