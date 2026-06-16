/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.core.commons.json;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.core.commons.exception.VigletException;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON validity check and object-to-string serialization.
 *
 * <p>Neutral home (Block Q / T375) for the JSON helpers the three products
 * hand-synced identically: {@link #isValidJson(String)} (object <em>or</em>
 * array, via {@code org.json}) and {@link #asJsonString(Object)} (Jackson 3,
 * tolerant of empty beans). Deliberately neutral: no {@code Tur*} naming and no
 * dependency on any product module.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class VigletJson {

    private VigletJson() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isValidJson(String test) {
        try {
            new JSONObject(test);
        } catch (JSONException ex) {
            try {
                new JSONArray(test);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }

    public static String asJsonString(final Object obj) throws VigletException {
        try {
            JsonMapper mapper = JsonMapper.builder()
                    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                    .build();

            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new VigletException(e);
        }
    }
}
