package com.viglet.turing.properties;

import com.viglet.turing.service.storage.TurStorageType;
import lombok.Getter;
import lombok.Setter;

/**
 * Storage configuration properties bound to {@code turing.storage.*}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Getter
@Setter
public class TurStorageProperty {
    private TurStorageType type;
    private TurMinioProperty minio;
    private TurFilesystemProperty filesystem;

    @Getter
    @Setter
    public static class TurFilesystemProperty {
        private String path = "./store/assets";
    }
}
