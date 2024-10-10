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

package com.viglet.turing.connector.aem.commons.context;

import lombok.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;


@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
public class TurAemSourceContext {
    private String id;
    private String url;
    private String username;
    private String password;
    private String rootPath;
    private String contentType;
    private String subType;
    private String turSNSite;
    private String siteName;
    private Locale defaultLocale;
    private String providerName;
    private String urlPrefix;
    private String oncePattern;
    @Builder.Default
    private Collection<TurAemLocalePathContext> localePaths = new HashSet<>();
}
