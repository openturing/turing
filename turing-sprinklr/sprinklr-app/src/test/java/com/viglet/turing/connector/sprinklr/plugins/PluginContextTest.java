package com.viglet.turing.connector.sprinklr.plugins;

import com.viglet.turing.connector.sprinklr.TurSprinklrApplication;
import com.viglet.turing.sprinklr.plugins.KeyValueTransformer;
import com.viglet.turing.sprinklr.plugins.PluginContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest ()
class PluginContextTest {

    @Autowired
    private PluginContext pluginContext;

    @Test
    void testGetPlugin() {
        pluginContext.getPlugin(KeyValueTransformer.class);
    }

}