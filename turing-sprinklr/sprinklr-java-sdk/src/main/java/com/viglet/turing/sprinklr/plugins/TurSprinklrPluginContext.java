package com.viglet.turing.sprinklr.plugins;

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
        throw new TurRuntimeException("Plugin not found" + pluginClass.getName());
    }
}
