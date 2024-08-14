package com.viglet.turing.commons.cache;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class TurCustomClassCache {
    private TurCustomClassCache() {
        throw new IllegalStateException("Custom Class Cache class");
    }
    private static final Map<String, Object> customClassMap = new HashMap<>();

    public static Optional<Object> getCustomClassMap(String className) {
        if (!customClassMap.containsKey(className)) {
            log.info("Custom class {} not found in memory, instancing...", className);
            try {
                customClassMap.put(className, Objects.requireNonNull(Class.forName(className)
                        .getDeclaredConstructor().newInstance()));
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException | ClassNotFoundException e) {
                log.error(e.getMessage(), e);
                return Optional.empty();
            }
        }
        return Optional.ofNullable(customClassMap.get(className));
    }
}
