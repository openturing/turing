package com.viglet.turing.connector.sprinklr.commons.tools;

import com.viglet.turing.sprinklr.plugins.KeyValueTransformer;
import com.viglet.turing.sprinklr.plugins.PluginContext;
import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KeyValueTransformerTool {
    private PluginContext pluginContext;

    public KeyValueTransformerTool(PluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }
    /**
     * Transforma uma chave em um valor usando o `KeyValueTransformer` e o arquivo especificado.
     *
     * @param key  A chave que será transformada.
     * @param file O nome do arquivo contendo o mapeamento.
     * @return O valor transformado ou null se o valor não for encontrado.
     */
    public String transform(String key, String file) {
//        log.debug("Teste para ver se carrou plugin{}", pluginContext.getPlugin(KeyValueTransformer.class).getName());
        // Recupera o plugin KeyValueTransformer
        KeyValueTransformer transformer = pluginContext.getPlugin(KeyValueTransformer.class);
        if (transformer == null) {
            log.error("KeyValueTransformer not found in PluginContext.");
            return null;
        }
        transformer.loadMapping(file);
        // Realiza a transformação
        return transformer.transform(key, file);
    }

}
