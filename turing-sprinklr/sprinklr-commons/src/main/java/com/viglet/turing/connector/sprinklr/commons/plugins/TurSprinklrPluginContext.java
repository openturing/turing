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

package com.viglet.turing.connector.sprinklr.commons.plugins;

import com.viglet.turing.commons.exception.TurRuntimeException;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@ToString
public class TurSprinklrPluginContext {
    List<TurSprinklrPlugin> initializedPlugins = new ArrayList<>();

    public TurSprinklrPluginContext(List<TurSprinklrPlugin> initializedPlugins){
        this.initializedPlugins = initializedPlugins;
    }

    public TurSprinklrPluginContext(){
    }

    public void addPlugin(TurSprinklrPlugin plugin){
        initializedPlugins.add(plugin);
    }

    public <T extends TurSprinklrPlugin> T getPlugin(Class<T> pluginClass){
        for (TurSprinklrPlugin plugin : initializedPlugins){
            if (pluginClass.isInstance(plugin)){
                return pluginClass.cast(plugin);
            }
        }
        throw new TurRuntimeException(pluginClass.getName().concat(" plugin not found") );
    }
}
