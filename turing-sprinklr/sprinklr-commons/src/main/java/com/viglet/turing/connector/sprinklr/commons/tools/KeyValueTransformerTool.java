package com.viglet.turing.connector.sprinklr.commons.tools;

import com.viglet.turing.sprinklr.plugins.TurSprinklrKeyValueTransformer;
import com.viglet.turing.sprinklr.plugins.TurSprinklrPluginContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KeyValueTransformerTool {
    private final TurSprinklrPluginContext pluginContext;

    public KeyValueTransformerTool(TurSprinklrPluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }
    /**
     * Transforms a key into a value using the `KeyValueTransformer` and the specified file.
     *
     * @param key The key that will be transformed.
     * @param file The name of the file containing the mapping.
     * @return The transformed value, or null if the value is not found.
     */
    public String transform(String key, String file) {
        // Retrieves the KeyValueTransformer plugin
        TurSprinklrKeyValueTransformer transformer = pluginContext.getPlugin(TurSprinklrKeyValueTransformer.class);
        if (transformer == null) {
            log.error("KeyValueTransformer not found in PluginContext.");
            return null;
        }
        transformer.loadMapping(file);
        // Perform the transformation
        return transformer.transform(key, file);
    }

}
