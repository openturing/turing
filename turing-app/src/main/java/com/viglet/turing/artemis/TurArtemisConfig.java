/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.artemis;

import com.viglet.turing.sn.TurSNConstants;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.springframework.boot.artemis.autoconfigure.ArtemisConfigurationCustomizer;
import org.springframework.stereotype.Component;

/**
 * Explicit Artemis broker tuning to avoid producer flow-control hangs.
 *
 * Defaults in Artemis 2.x base globalMaxSize on the JVM's free heap at
 * startup, which combined with persistent journals can cause producers
 * to hang on credit acquisition between indexing runs even when the
 * queue is empty (the broker still accounts for memory not yet
 * reclaimed by GC, or for a near-full disk threshold).
 *
 * Pinning explicit limits and using PAGE policy keeps producers
 * non-blocking: when the address is full, messages spill to disk
 * instead of blocking the producer.
 */
@Component
public class TurArtemisConfig implements ArtemisConfigurationCustomizer {

    private static final long ONE_GB = 1024L * 1024L * 1024L;
    private static final long FIVE_HUNDRED_MB = 512L * 1024L * 1024L;
    private static final int TEN_MB = 10 * 1024 * 1024;

    @Override
    public void customize(Configuration configuration) {
        configuration.setGlobalMaxSize(ONE_GB);
        configuration.setMaxDiskUsage(95);

        AddressSettings settings = new AddressSettings()
                .setMaxSizeBytes(FIVE_HUNDRED_MB)
                .setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE)
                .setPageSizeBytes(TEN_MB);

        configuration.getAddressSettings().put(TurSNConstants.INDEXING_QUEUE, settings);
        configuration.getAddressSettings().put("#", settings);
    }
}
