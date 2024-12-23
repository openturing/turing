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

/**
 * The {@code TurSprinklrPluginContext} class manages a list of initialized plugins.
 * This context is used as a container for plugins and provide a way to access them.
 * So, you need to send it to the places(custom classes or tools) that you want to use the plugins.
 * 
 * <p>Example usage:
 * <pre>
 * {@code
 * TurSprinklrPluginContext context = new TurSprinklrPluginContext();
 * context.addPlugin(new SomePlugin());
 * }
 * </pre>
 * <p>Then, you can access the plugin in your class:
 * <pre>
 * {@code
 * SomePlugin plugin = context.getPlugin(SomePlugin.class);
 * plugin.doSomething();
 * }
 * </pre>
 * 
 * <p>Method Summary:
 * <ul>
 *   <li>{@link #addPlugin(TurSprinklrPlugin)} - Adds a plugin to the context.</li>
 *   <li>{@link #getPlugin(Class)} - Retrieves a plugin from the context by its class type.</li>
 * </ul>
 * 
 * @see TurSprinklrPlugin
 * @author Gabriel F. Gomazako
 * @since 0.3.9
 */
@ToString
public class TurSprinklrPluginContext {
    List<TurSprinklrPlugin> initializedPlugins = new ArrayList<>();

    public TurSprinklrPluginContext(List<TurSprinklrPlugin> initializedPlugins){
        this.initializedPlugins = initializedPlugins;
    }

    public TurSprinklrPluginContext(){
    }

    /**
     * Adds a plugin to the list of initialized plugins.
     *
     * @param plugin the TurSprinklrPlugin instance to be added
     */
    public void addPlugin(TurSprinklrPlugin plugin){
        initializedPlugins.add(plugin);
    }

    /**
     * Retrieves an instance of the specified plugin class from the initialized plugins.
     *
     * @param <T> the type of the plugin, which must be a subclass of TurSprinklrPlugin
     * @param pluginClass the class of the plugin to retrieve
     * @return an instance of the specified plugin class
     * @throws TurRuntimeException if the plugin is not found
     */
    public <T extends TurSprinklrPlugin> T getPlugin(Class<T> pluginClass){
        for (TurSprinklrPlugin plugin : initializedPlugins){
            if (pluginClass.isInstance(plugin)){
                return pluginClass.cast(plugin);
            }
        }
        throw new TurRuntimeException("Plugin not found" + pluginClass.getName());
    }
}
