package com.viglet.turing.connector.sprinklr.commons.tools;

import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrKeyValueTransformer;
import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrPluginContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KeyValueTransformerTool {
    private final TurSprinklrPluginContext pluginContext;

    TurSprinklrNotFoundAction notFoundAction = TurSprinklrNotFoundAction.KEEP_KEY;
    String defaultNotFoundText = "Key Value Transformer Plugin: Key not found";

    public KeyValueTransformerTool(TurSprinklrPluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }
    /**
     * Transforms a key into a value using the `KeyValueTransformer` and the specified file.
     *
     * @param key The key that will be transformed. Key can be null.
     * @param file The name of the file containing the mapping.
     * @return The transformed value, or if the key is not found the return value will depend on the configuration.
     */
    public String transform(String key, String file) {
        // Retrieves the KeyValueTransformer plugin
        TurSprinklrKeyValueTransformer transformer = pluginContext.getPlugin(TurSprinklrKeyValueTransformer.class);
        if (transformer == null) {
            log.error("KeyValueTransformer not found in PluginContext.");
            return null;
        }
        if (key == null) {
            log.warn("Key is Null");
        }
        transformer.loadMapping(file);
        // Perform the transformation

        var transformed = transformer.transform(key, file);

        if (transformed == null) {
            return treatNotFound(key, file);
        }
        return transformed;
    }


    private String treatNotFound(String key, String file) {
        log.warn("Key not found: {} in file: {}", key, file);
        return switch (notFoundAction) {
            case DEFAULT_VALUE -> defaultNotFoundText;
            case KEEP_KEY -> key;
            default -> null;
        };
    }

    /**
     * Configures the action to be taken when a key is not found and sets the default text for such cases.
     *
     * @param action the action to be taken when a key is not found
     */
    public void configure(TurSprinklrNotFoundAction action) {
        notFoundAction = action;
    }

    /**
     * Configures the action to be taken when a key is not found and sets the default text for such cases.
     *
     * @param action the action to be taken when a key is not found
     * @param defaultText the default text to be used when a key is not found if action is DEFAULT_VALUE
     */
    public void configure(TurSprinklrNotFoundAction action, String defaultText) {
        notFoundAction = action;
        defaultNotFoundText = defaultText;
    }

}
