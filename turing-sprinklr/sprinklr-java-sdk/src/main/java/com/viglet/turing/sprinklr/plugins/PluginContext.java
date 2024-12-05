package com.viglet.turing.sprinklr.plugins;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PluginContext {
    List<TurSprinklrPlugin> initializedPlugins;

    @Autowired
    public PluginContext(List<TurSprinklrPlugin> initializedPlugins){
        this.initializedPlugins = initializedPlugins;
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
