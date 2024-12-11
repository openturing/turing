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
 * @author Gabriel F. Gomazako
 * @since 0.3.9
 */
@Slf4j
public class TurSprinklrKeyValueTransformer implements TurSprinklrPlugin {

    private final Set<String> files = new HashSet<>();

    private final Map<String, Map<String, String>> mappings = new HashMap<>();



    TurSprinklrNotFound notFoundAction = TurSprinklrNotFound.DEFAULT_VALUE;

    String defaultNotFoundText = "Key Value Transformer Plugin: Key not found";

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
     * @param key  the key to be transformed
     * @param file the file from which the mapping is obtained
     * @return the transformed value corresponding to the key, or a treated value if the key is not found
     */
    public String transform(String key, String file) {
        Map<String, String> mapping = getMapping(file);
        return mapping.getOrDefault(key, treatNotFound(key, file));
    }

    private String treatNotFound(String key, String file) {
        log.warn("Key not found: {} in file: {}", key, file);
        return switch (notFoundAction) {
            case DEFAULT_VALUE -> defaultNotFoundText;
            case KEEP_KEY -> key;
            default -> null;
        };
    }

    public void configure(TurSprinklrNotFound action) {
        notFoundAction = action;
    }

    /**
     * Configures the action to be taken when a key is not found and sets the default text for such cases.
     *
     * @param action the action to be taken when a key is not found
     * @param defaultText the default text to be used when a key is not found
     */
    public void configure(TurSprinklrNotFound action, String defaultText) {
        notFoundAction = action;
        defaultNotFoundText = defaultText;
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