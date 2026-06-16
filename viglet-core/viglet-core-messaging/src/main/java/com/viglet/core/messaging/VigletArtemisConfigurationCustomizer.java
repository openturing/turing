/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.messaging;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.springframework.boot.artemis.autoconfigure.ArtemisConfigurationCustomizer;

/**
 * Explicit embedded-Artemis broker tuning to avoid producer flow-control hangs,
 * shared across Viglet products.
 *
 * <p>Defaults in Artemis 2.x base {@code globalMaxSize} on the JVM's free heap at
 * startup, which combined with persistent journals can cause producers to hang on
 * credit acquisition between indexing runs even when the queue is empty (the
 * broker still accounts for memory not yet reclaimed by GC, or for a near-full
 * disk threshold).</p>
 *
 * <p>Pinning explicit limits and using the {@code PAGE} policy keeps producers
 * non-blocking: when the address is full, messages spill to disk instead of
 * blocking the producer.</p>
 *
 * <p>Each product registers this as a bean (or a thin subclass) and supplies its
 * own indexing-queue address name(s); the catch-all {@code "#"} address always
 * receives the same settings.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletArtemisConfigurationCustomizer implements ArtemisConfigurationCustomizer {

    /** Default broker-wide memory ceiling: 1 GiB. */
    public static final long DEFAULT_GLOBAL_MAX_SIZE = 1024L * 1024L * 1024L;
    /** Default per-address memory ceiling before paging kicks in: 512 MiB. */
    public static final long DEFAULT_ADDRESS_MAX_SIZE_BYTES = 512L * 1024L * 1024L;
    /** Default page file size: 10 MiB. */
    public static final int DEFAULT_PAGE_SIZE_BYTES = 10 * 1024 * 1024;
    /** Default max disk usage percentage before the broker blocks: 95%. */
    public static final int DEFAULT_MAX_DISK_USAGE = 95;

    /** Catch-all address-settings match — applies the same tuning to every address. */
    private static final String CATCH_ALL_ADDRESS = "#";

    private final long globalMaxSize;
    private final int maxDiskUsage;
    private final long addressMaxSizeBytes;
    private final int pageSizeBytes;
    private final String[] namedAddresses;

    /**
     * Tunes the broker with the default limits, applying the PAGE policy to each
     * of the given named addresses plus the catch-all address.
     *
     * @param namedAddresses product-specific address names (e.g. the indexing queue)
     */
    public VigletArtemisConfigurationCustomizer(String... namedAddresses) {
        this(DEFAULT_GLOBAL_MAX_SIZE, DEFAULT_MAX_DISK_USAGE, DEFAULT_ADDRESS_MAX_SIZE_BYTES,
                DEFAULT_PAGE_SIZE_BYTES, namedAddresses);
    }

    /**
     * Tunes the broker with explicit limits.
     *
     * @param globalMaxSize       broker-wide memory ceiling in bytes
     * @param maxDiskUsage        max disk usage percentage before the broker blocks
     * @param addressMaxSizeBytes per-address memory ceiling before paging
     * @param pageSizeBytes       page file size in bytes
     * @param namedAddresses      product-specific address names
     */
    public VigletArtemisConfigurationCustomizer(long globalMaxSize, int maxDiskUsage,
            long addressMaxSizeBytes, int pageSizeBytes, String... namedAddresses) {
        this.globalMaxSize = globalMaxSize;
        this.maxDiskUsage = maxDiskUsage;
        this.addressMaxSizeBytes = addressMaxSizeBytes;
        this.pageSizeBytes = pageSizeBytes;
        this.namedAddresses = namedAddresses != null ? namedAddresses.clone() : new String[0];
    }

    @Override
    public void customize(Configuration configuration) {
        configuration.setGlobalMaxSize(globalMaxSize);
        configuration.setMaxDiskUsage(maxDiskUsage);

        AddressSettings settings = new AddressSettings()
                .setMaxSizeBytes(addressMaxSizeBytes)
                .setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE)
                .setPageSizeBytes(pageSizeBytes);

        for (String address : namedAddresses) {
            configuration.getAddressSettings().put(address, settings);
        }
        configuration.getAddressSettings().put(CATCH_ALL_ADDRESS, settings);
    }
}
