package com.viglet.turing.connector.sprinklr.plugins.tools;

import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrPluginContext;
import com.viglet.turing.connector.sprinklr.commons.tools.KeyValueTransformerTool;
import com.viglet.turing.connector.sprinklr.commons.tools.TurSprinklrNotFoundAction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class KeyValueTransformerToolTest {
    final String testFile = "src/test/resources/test-mapping.txt";

    @Autowired
    private TurSprinklrPluginContext context;
    /**
     * Test method for the scenario where the key is not found and the action is set to DEFAULT_VALUE.
     * 
     * This test verifies that when the key does not exist in the provided context, the 
     * KeyValueTransformerTool returns the default text as specified by the DEFAULT_VALUE action.
     * 
     * The test performs the following steps:
     * 1. Configures the KeyValueTransformerTool with the DEFAULT_VALUE action.
     * 2. Attempts to transform a non-existent key.
     * 3. Asserts that the returned value matches the expected default text.
     */
    @Test
    void notFoundDEFAULT_VALUE() {
        KeyValueTransformerTool tool = new KeyValueTransformerTool(context);

        tool.configure(TurSprinklrNotFoundAction.DEFAULT_VALUE);
        var value = tool.transform("This key does not exist", testFile);

        Assertions.assertEquals("If is set to DEFAULT_VALUE action, value should be equals to default text", value, "Key Value Transformer Plugin: Key not found");
    }

    /**
     * Test case for the KeyValueTransformerTool when the action is set to KEEP_KEY.
     * This test verifies that if the key does not exist, the value returned should be equal to the key.
     */
    @Test
    void notFoundKEEP_KEY() {
        KeyValueTransformerTool tool = new KeyValueTransformerTool(context);

        tool.configure(TurSprinklrNotFoundAction.KEEP_KEY);
        var value = tool.transform("This key does not exist", testFile);

        Assertions.assertEquals( "If is set to KEEP_KEY action, value should be equals to key", value, "This key does not exist");
    }

    /**
     * Test method for the {@link KeyValueTransformerTool#transform(String, String)} method.
     * <p>
     * This test verifies that when the {@link KeyValueTransformerTool} is configured with
     * {@link TurSprinklrNotFoundAction#NULL}, the transform method returns null for a key that does not exist.
     * </p>
     * <p>
     * Steps:
     * <ol>
     *   <li>Configure the {@link KeyValueTransformerTool} with {@link TurSprinklrNotFoundAction#NULL}.</li>
     *   <li>Invoke the {@link KeyValueTransformerTool#transform(String, String)} method with a non-existent key.</li>
     *   <li>Assert that the returned value is null.</li>
     * </ol>
     * </p>
     */
    @Test
    void notFoundNULL() {
        KeyValueTransformerTool tool = new KeyValueTransformerTool(context);

        tool.configure(TurSprinklrNotFoundAction.NULL);
        var value = tool.transform("This key does not exist", testFile);

        Assertions.assertNull("If is set to NULL action, value should be null", value);
    }

    /**
     * Tests the behavior of the KeyValueTransformerTool when it receives a null key.
     * 
     * This test ensures that when the tool is configured with the KEEP_KEY action
     * and a null key is provided, the transform method returns null.
     * 
     * The expected behavior is that if the key is null, the value should also be null.
     */
    @Test
    void toolReceivesNullKey() {
        KeyValueTransformerTool tool = new KeyValueTransformerTool(context);

        tool.configure(TurSprinklrNotFoundAction.KEEP_KEY);
        var value = tool.transform(null, testFile);

        Assertions.assertNull("If key is null, value should be null", value);
    }

    @Test
    void toolReceivesNullKey2() {
        KeyValueTransformerTool tool = new KeyValueTransformerTool(context);

        tool.configure(TurSprinklrNotFoundAction.DEFAULT_VALUE);
        var value = tool.transform(null, testFile);

        Assertions.assertEquals("If key is null, value should be equals to default text", value, "Key Value Transformer Plugin: Key not found");
    }

    @Test
    void toolReceivesNullKey3() {
        KeyValueTransformerTool tool = new KeyValueTransformerTool(context);

        tool.configure(TurSprinklrNotFoundAction.NULL);
        var value = tool.transform(null, testFile);

        Assertions.assertNull("If key is null, value should be null", value);
    }

    /**
     * Test method for {@link KeyValueTransformerTool#transform(String, String)}.
     * <p>
     * This test verifies that the {@link KeyValueTransformerTool} throws an 
     * {@link IllegalArgumentException} when attempting to transform a key using 
     * a non-existent file.
     * </p>
     * <p>
     * The method {@code transform} is called with a key that does not exist and 
     * a filename that does not exist. The expected behavior is that an 
     * {@link IllegalArgumentException} is thrown.
     * </p>
     */
    @Test
    void toolReceivesNonExistentFile() {
        KeyValueTransformerTool tool = new KeyValueTransformerTool(context);

        Assertions.assertThrows(IllegalArgumentException.class, () ->
            tool.transform("This key does not exist", "non-existent-file.txt")
        );
    }

}
