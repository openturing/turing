/*
 *
 * Copyright (C) 2016-2025 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector.plugin.sprinklr.plugins;

import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrKeyValueTransformer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TurSprinklrKeyValueTransformerTest {

    @Autowired
    private TurSprinklrKeyValueTransformer transformer;

    @Test
    void loadMapping() {
        // Path to test file (adjust to correct location)
        String testFile = "src/test/resources/test-mapping.txt";

        // Call the loadMapping method
        transformer.loadMapping(testFile);

        // Checks if the mapping has been loaded
        Map<String, String> mapping = transformer.getMapping(testFile);
        assertNotNull(mapping, "Mapping should not be null after loading");
        assertFalse(mapping.isEmpty(), "Mapping should not be empty after loading");

        // Checks if a specific key was loaded correctly
        assertEquals("action", mapping.get("424123421312"), "Value for key '424123421312' should be 'action'");
    }

    @Test
    void getMapping() {
        // Path to test file (adjust to correct location)
        String testFile = "src/test/resources/test-mapping.txt";

        // Make sure the mapping has been loaded
        transformer.loadMapping(testFile);

        // Retrieves the mapping and checks
        Map<String, String> mapping = transformer.getMapping(testFile);
        assertNotNull(mapping, "Mapping should not be null");
        // There are two lines with the same key
        assertEquals(2, mapping.size(), "Mapping should have 2 entries");

        // Checks if a specific key is present
        assertTrue(mapping.containsKey("235521957944"), "Mapping should contain key '235521957944'");
    }

    @Test
    void transform() {
        // Path to test file (adjust to correct location)
        String testFile = "src/test/resources/test-mapping.txt";

        // Make sure the mapping has been loaded
        transformer.loadMapping(testFile);

        // Test the transformation
        String transformedValue = transformer.transform("424123421312", testFile);
        assertEquals("action", transformedValue, "Transformed value should be 'action'");
    }

    @Test
    void loadMappingTransFromLoadSameMapTransform() {
        // Path to test file (adjust to correct location)
        String testFile = "src/test/resources/test-mapping.txt";

        // Call the loadMapping method
        transformer.loadMapping(testFile);

        var transformedValue = transformer.transform("424123421312", testFile);
        assertEquals("action", transformedValue, "Transformed value should be 'action'");
        transformer.loadMapping(testFile);
        transformedValue = transformer.transform("235521957944", testFile);
        assertEquals("adventure", transformedValue, "Transformed value should be 'adventure'");
    }

    @Test
    void getName() {
        String name = transformer.getName();
        assertNotNull(name);
        assertEquals("Key Value Transformer Plugin", transformer.getName());

    }

    @Test
    void getDescription() {
        String description = transformer.getDescription();
        assertNotNull(description);
        assertEquals("Transforms a value into another value based on a key-value mapping file", transformer.getDescription());
    }
}