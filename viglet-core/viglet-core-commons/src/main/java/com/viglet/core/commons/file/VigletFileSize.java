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
package com.viglet.core.commons.file;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A file size expressed simultaneously in bytes, kilobytes and megabytes, each
 * rounded to two decimals.
 *
 * <p>Neutral home (Block Q / T375) for the byte→KB→MB conversion the three
 * products hand-synced identically. Deliberately neutral: no {@code Tur*}
 * naming and no dependency on any product module.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletFileSize {

    private final float bytes;
    private final float kiloBytes;
    private final float megaBytes;

    public VigletFileSize() {
        this(0f);
    }

    public VigletFileSize(float bytes) {
        this.bytes = twoDecimalFloat(bytes);
        this.kiloBytes = twoDecimalFloat(this.bytes / 1024);
        this.megaBytes = twoDecimalFloat(this.kiloBytes / 1024);
    }

    /**
     * Builds a size from already-computed values, bypassing the conversion. Kept
     * so a product DTO with an explicit all-args constructor can delegate here.
     */
    public VigletFileSize(float bytes, float kiloBytes, float megaBytes) {
        this.bytes = bytes;
        this.kiloBytes = kiloBytes;
        this.megaBytes = megaBytes;
    }

    private static float twoDecimalFloat(float value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).floatValue();
    }

    public float getBytes() {
        return bytes;
    }

    public float getKiloBytes() {
        return kiloBytes;
    }

    public float getMegaBytes() {
        return megaBytes;
    }

    @Override
    public String toString() {
        return "VigletFileSize(bytes=" + bytes + ", kiloBytes=" + kiloBytes
                + ", megaBytes=" + megaBytes + ")";
    }
}
