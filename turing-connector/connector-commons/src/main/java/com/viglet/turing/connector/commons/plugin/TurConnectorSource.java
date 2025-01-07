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

package com.viglet.turing.connector.commons.plugin;

import lombok.Data;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

@Data
public class TurConnectorSource {
    private String systemId;
    private String transactionId;
    private Collection<String> sites;
    private String providerName;
    private Locale locale;

    public TurConnectorSource(String systemId, Collection<String> sites, String providerName,
                              Locale locale) {
        this.systemId = systemId;
        this.transactionId = UUID.randomUUID().toString();
        this.sites = sites;
        this.providerName = providerName;
        this.locale = locale;
    }
}
