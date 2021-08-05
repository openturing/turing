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
package com.viglet.turing.aem.core.impl;

import org.apache.sling.settings.SlingSettingsService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import com.viglet.turing.aem.core.TurAemServerConfiguration;
import com.viglet.turing.aem.core.TurAemMySimpleService;

@Component(service = TurAemServerConfiguration.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = TurAemMySimpleService.class)
public class TurAemServerConfigurationImpl implements TurAemServerConfiguration {

    private TurAemMySimpleService config;

    private String TuringProtocol;

    private String TuringServerName;

    private String TuringServerPort;

    private String TuringCoreName;

    private String contentPagePath;

    @Activate
    public void activate(TurAemMySimpleService config) {
        this.config = config;

        // Populate the TuringProtocol data member
        this.TuringProtocol = config.protocolValue();
        this.TuringServerName = config.serverName();
        this.TuringServerPort = config.serverPort();
        this.TuringCoreName = config.serverCollection();
        this.contentPagePath = config.serverPath();

    }

    public String getTuringProtocol() {
        return this.TuringProtocol;
    }

    public String getTuringServerName() {
        return this.TuringServerName;
    }

    public String getTuringServerPort() {
        return this.TuringServerPort;
    }

    public String getTuringCoreName() {
        return this.TuringCoreName;
    }

    public String getContentPagePath() {
        return this.contentPagePath;
    }
}