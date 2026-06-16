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
 * No-op storage implementation used when storage is disabled
 * ({@code viglet.storage.type=none}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletNoOpStorageService implements VigletStorageService {

    private static final String STORAGE_NOT_CONFIGURED = "Storage is not configured.";

    @Override
    public VigletStorageType getType() {
        return VigletStorageType.NONE;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public List<VigletStorageObject> listObjects(String prefix) {
        return List.of();
    }

    @Override
    public List<VigletStorageObject> listAllObjects() {
        return List.of();
    }

    @Override
    public InputStream downloadObject(String objectName) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }

    @Override
    public VigletStorageObjectStat statObject(String objectName) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }

    @Override
    public void createFolder(String folderPath) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }

    @Override
    public void uploadStream(String objectName, InputStream inputStream, long size, String contentType) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }

    @Override
    public void deleteObjectsWithPrefix(String prefix) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }

    @Override
    public void deleteObject(String objectName) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }
}
