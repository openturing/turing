package com.viglet.turing.connector.sprinklr.plugins;

import com.viglet.turing.sprinklr.plugins.TurSprinklrKeyValueTransformer;
import com.viglet.turing.sprinklr.plugins.TurSprinklrPluginContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest ()
class TurSprinklrPluginContextTest {

    @Autowired
    private TurSprinklrPluginContext pluginContext;

    @Test
    void testGetPlugin() {
        pluginContext.getPlugin(TurSprinklrKeyValueTransformer.class);
    }

}