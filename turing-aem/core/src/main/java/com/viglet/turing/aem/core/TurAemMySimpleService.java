/*
 * Copyright (C) 2021 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.aem.core;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "AEM Turing Search - Turing Configuration Service", description = "Service Configuration")
public @interface TurAemMySimpleService {

    @AttributeDefinition(name = "Protocol", defaultValue = "http", description = "Configuration value")
    String protocolValue();

    @AttributeDefinition(name = "Turing Server Name", defaultValue = "localhost", description = "Server name or IP address")
    String serverName();

    @AttributeDefinition(name = "Turing Server Port", defaultValue = "8983", description = "Server port")
    String serverPort();

    @AttributeDefinition(name = "Turing Core Name", defaultValue = "collection", description = "Core name in Turing server")
    String serverCollection();

    @AttributeDefinition(name = "Content page path", defaultValue = "/content/we-retail", description = "Content page path from where Turing has to index the pages")
    String serverPath();

}