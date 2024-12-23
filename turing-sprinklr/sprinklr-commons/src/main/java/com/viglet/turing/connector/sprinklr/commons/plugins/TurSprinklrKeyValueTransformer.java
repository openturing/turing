/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.turing.connector.sprinklr.commons.plugins;

import com.viglet.turing.commons.exception.TurRuntimeException;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.*;

/**
 * The {@code TurSprinklrKeyValueTransformer} is a {@code TurSprinklrPlugin}
 * and provides functionality to load key-value mappings from files and transform keys using these mappings.
 * 
 * <p>This class maintains a set of loaded files and a map of mappings for each file. It provides methods
 * to load mappings from files, retrieve mappings, and transform keys using the loaded mappings.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * TurSprinklrKeyValueTransformer transformer = new TurSprinklrKeyValueTransformer();
 * transformer.loadMapping("path/to/mapping/file");
 * String transformedValue = transformer.transform("key", "path/to/mapping/file");
 * </pre>
 * 
 * <p>The mapping file should have the following structure:</p>
 * <pre>
 * key1 value1
 * key2 value2
 * key3 value3
 * </pre>
 * 
 * <p>Each line in the file should contain a key and a value separated by a space. Duplicate keys will be logged
 * as warnings, and invalid lines will cause a {@code TurRuntimeException} to be thrown.</p>
 * 
 * @author Gabriel F. Gomazako
 * @since 0.3.9
 */
@Slf4j
public class TurSprinklrKeyValueTransformer implements TurSprinklrPlugin {

    private final Set<String> files = new HashSet<>();

    private final Map<String, Map<String, String>> mappings = new HashMap<>();

    /**
     * Loads a mapping from the specified file if it has not been loaded already.
     *
     * @param file the path to the file to load the mapping from
     */
    public void loadMapping(String file) {

        if (!files.contains(file)) {
            files.add(file);
            var newMapping = createMappingFromFile(file);
            mappings.putIfAbsent(file, newMapping);
        }
    }

    /**
     * Creates a mapping from a file. Example of the structure of the file:
     *
     * <pre>
     * 424123421312 action
     * 235521957944 adventure
     * 235521957944 comedy
     * </pre>
     *
     * @param file The file to create the mapping from
     * @return a map containing the key-value pairs from the file
     */
    private Map<String, String> createMappingFromFile(String file) {
        File mappingFile = new File(file);
        HashMap<String, String> newMapping = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(mappingFile));

            while (reader.ready()) {
                String line = reader.readLine();
                line = line.trim();
                String[] keyValue = line.split(" ");

                if (keyValue.length != 2) {
                    reader.close();
                    throw new TurRuntimeException("Invalid line: " + line);
                }

                if (newMapping.putIfAbsent(keyValue[0], keyValue[1]) != null) {
                    log.warn("Duplicated key: {}", keyValue[0]);
                }
            }
            reader.close();
            return newMapping;
        } catch (FileNotFoundException e) {
            log.error("Mapping file not found: {}", file, e);
            throw new IllegalArgumentException("The specified file does not exist: " + file, e);
        } catch (IOException e) {
            log.error("Error reading file: {}", file, e);
            throw new UncheckedIOException("Error occurred while reading the file: " + file, e);
        }
    }

    public Map<String, String> getMapping(String file) {
        return mappings.get(file);
    }

    /**
     * Transforms the given key using the mapping obtained from the specified file.
     *
     * @param key  the key to be transformed, if is null, the value returned will be null.
     * @param file the file from which the mapping is obtained
     * @return the transformed value corresponding to the key, or a treated value if the key is not found
     */
    public String transform(String key, String file) {
        Map<String, String> mapping = getMapping(file);
        return mapping.getOrDefault(key, null);
    }

    @Override
    public String getName() {
        return "Key Value Transformer Plugin";
    }

    @Override
    public String getDescription() {
        return "Transforms a value into another value based on a key-value mapping file";
    }
}