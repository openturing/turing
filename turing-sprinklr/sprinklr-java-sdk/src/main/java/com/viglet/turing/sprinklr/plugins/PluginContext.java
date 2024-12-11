package com.viglet.turing.sprinklr.plugins;

import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@ToString
public class PluginContext {
    List<TurSprinklrPlugin> initializedPlugins = new ArrayList<TurSprinklrPlugin>();

    public PluginContext(List<TurSprinklrPlugin> initializedPlugins){
        this.initializedPlugins = initializedPlugins;
    }

    public PluginContext(){
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
        throw new RuntimeException("Plugin not found" + pluginClass.getName());
    }
}
