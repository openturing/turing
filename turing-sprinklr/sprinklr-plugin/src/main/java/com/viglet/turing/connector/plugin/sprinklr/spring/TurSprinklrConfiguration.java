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

package com.viglet.turing.connector.plugin.sprinklr.spring;

import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrKeyValueTransformer;
import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrPluginContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:application-sprinklr.properties")
public class TurSprinklrConfiguration {
    @Bean
    public TurSprinklrPluginContext pluginContext(TurSprinklrKeyValueTransformer transformer) {
        TurSprinklrPluginContext pluginContext = new TurSprinklrPluginContext();
        pluginContext.addPlugin(transformer);
        return pluginContext;
    }

    @Bean
    public TurSprinklrKeyValueTransformer keyValueTransformer() {
        return new TurSprinklrKeyValueTransformer();
    }
}
