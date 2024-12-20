package com.viglet.turing.connector.sprinklr.plugins.tools;

import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrPluginContext;
import com.viglet.turing.connector.sprinklr.commons.tools.KeyValueTransformerTool;
import com.viglet.turing.connector.sprinklr.commons.tools.TurSprinklrNotFoundAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.util.AssertionErrors.assertEquals;
import static org.springframework.test.util.AssertionErrors.assertNull;

@SpringBootTest
class KeyValueTransformerToolTest {
    final String testFile = "src/test/resources/test-mapping.txt";

    @Autowired
    private TurSprinklrPluginContext context;
    @Test
    void notFoundDEFAULT_VALUE() {
        KeyValueTransformerTool tool = new KeyValueTransformerTool(context);

        tool.configure(TurSprinklrNotFoundAction.DEFAULT_VALUE);
        var value = tool.transform("This key does not exist", testFile);

        assertEquals("If is set to DEFAULT_VALUE action, value should be equals to default text", value, "Key Value Transformer Plugin: Key not found");
    }

    @Test
    void notFoundKEEP_KEY() {
        KeyValueTransformerTool tool = new KeyValueTransformerTool(context);

        tool.configure(TurSprinklrNotFoundAction.KEEP_KEY);
        var value = tool.transform("This key does not exist", testFile);

        assertEquals("If is set to KEEP_KEY action, value should be equals to key", value, "This key does not exist");
    }

    @Test
    void notFoundNULL() {
        KeyValueTransformerTool tool = new KeyValueTransformerTool(context);

        tool.configure(TurSprinklrNotFoundAction.NULL);
        var value = tool.transform("This key does not exist", testFile);

        assertNull("If is set to NULL action, value should be null", value);
    }

    @Test
    void toolReceivesNullKey() {
        KeyValueTransformerTool tool = new KeyValueTransformerTool(context);

        tool.configure(TurSprinklrNotFoundAction.KEEP_KEY);
        var value = tool.transform(null, testFile);

        assertNull("If key is null, value should be null", value);
    }
}
