package com.viglet.turing.commons.cache;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
public class TurRequestBodyCache {
    private TurRequestBodyCache() {
        throw new IllegalStateException("Request Body Cache class");
    }
    private static final Map<String, String> responseHttpCache = new HashMap<>();

}
