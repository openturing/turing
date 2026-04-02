package com.viglet.turing.api.system;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO that carries runtime system information to the frontend.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Builder
@Getter
@Setter
public class TurSystemInfoBean {

    private String appVersion;
    private DatabaseInfo database;
    private MemoryInfo memory;
    private DiskInfo disk;
    private ExternalServiceInfo mongodb;
    private ExternalServiceInfo storage;
    private String storageType;

    public record DatabaseInfo(
            String productName,
            String productVersion,
            String driverName,
            String driverVersion,
            String url,
            String status
    ) {}

    public record MemoryInfo(
            long maxMemory,
            long totalMemory,
            long usedMemory,
            long freeMemory,
            long totalPhysicalMemory,
            long freePhysicalMemory,
            long totalSwap,
            long freeSwap
    ) {}

    public record DiskInfo(
            long totalSpace,
            long usableSpace,
            long usedSpace
    ) {}

    public record ExternalServiceInfo(
            boolean enabled,
            String version,
            String endpoint,
            String status
    ) {}
}
