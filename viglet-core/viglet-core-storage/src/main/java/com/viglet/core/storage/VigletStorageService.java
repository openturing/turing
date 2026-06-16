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
package com.viglet.core.storage;

import java.io.InputStream;
import java.util.List;

/**
 * Abstraction for object storage operations (MinIO, filesystem, or no-op).
 *
 * <p>Lifted from Turing into {@code viglet-core} (Block Q / T368). The SPI is
 * deliberately framework-neutral and stream-based — no {@code MultipartFile},
 * no product model — so any Viglet product can depend on it. Web-layer
 * conveniences (multipart upload) and tenant scoping are added by the consuming
 * product as decorators over this interface.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface VigletStorageService {

    VigletStorageType getType();

    boolean isEnabled();

    /**
     * Backend initialization hook. Spring invokes it as {@code @PostConstruct}
     * on an auto-configured bean; a consumer constructing a backend by hand
     * must call it once before use. No-op by default.
     */
    default void init() {
        // no-op; filesystem/minio backends override
    }

    List<VigletStorageObject> listObjects(String prefix);

    List<VigletStorageObject> listAllObjects();

    InputStream downloadObject(String objectName);

    VigletStorageObjectStat statObject(String objectName);

    void createFolder(String folderPath);

    void uploadStream(String objectName, InputStream inputStream, long size, String contentType);

    void deleteObjectsWithPrefix(String prefix);

    void deleteObject(String objectName);

    default String guessContentType(String name) {
        return VigletStorageContentTypes.guessContentType(name);
    }
}
