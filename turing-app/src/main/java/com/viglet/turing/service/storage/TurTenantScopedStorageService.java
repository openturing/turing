/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.storage;

import java.io.InputStream;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.tenant.TurTenantContext;

import lombok.extern.slf4j.Slf4j;

/**
 * T269 / §XIV.4.3 — a {@link TurStorageService} decorator that prepends
 * {@code tenants/<tenantId>/} to every object key before delegating, so two
 * tenants' uploads never share a path on a shared bucket / filesystem root.
 *
 * <p>The prefix is added on the way in (uploads, downloads, deletes, listing
 * prefixes) and <strong>stripped</strong> from {@link #listObjects} /
 * {@link #listAllObjects} results, so callers stay oblivious to the tenant
 * partition. A key is also guarded against {@code ..} traversal that would
 * escape the tenant root.
 *
 * <p>When tenancy is off — or the tenant is the immutable {@code DEFAULT} — the
 * prefix is empty and every call passes straight through, so existing
 * single-tenant object layouts are untouched (no asset migration needed).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class TurTenantScopedStorageService implements TurStorageService {

    private final TurStorageService delegate;
    private final TurTenantContext tenantContext;

    public TurTenantScopedStorageService(TurStorageService delegate, TurTenantContext tenantContext) {
        this.delegate = delegate;
        this.tenantContext = tenantContext;
    }

    /** {@code tenants/<tenantId>/} for the current tenant, or {@code ""} when off/DEFAULT. */
    String currentPrefix() {
        if (!tenantContext.isTenancyEnabled()) {
            return "";
        }
        String tenant = tenantContext.resolveCurrentTenant();
        if (tenant == null || TurTenant.DEFAULT_TENANT_ID.equals(tenant)) {
            return "";
        }
        return "tenants/" + tenant + "/";
    }

    private String scopeKey(String key) {
        // T645 / §XXXVII.7 — key containment is now UNCONDITIONAL. Previously the
        // `..` guard only ran when a tenant prefix was active, so the most common
        // deployment (tenancy off / DEFAULT tenant → empty prefix) accepted a
        // traversal key verbatim. Reject `..` and backslashes and strip a leading
        // slash regardless of tenancy so no key can escape its (optional) prefix.
        String normalized = key == null ? "" : key;
        if (normalized.contains("..") || normalized.contains("\\")) {
            throw new IllegalArgumentException("Path traversal is not allowed in object key: " + key);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String prefix = currentPrefix();
        return prefix.isEmpty() ? normalized : prefix + normalized;
    }

    private TurAssetItem stripPrefix(TurAssetItem item, String prefix) {
        if (prefix.isEmpty() || item.name() == null || !item.name().startsWith(prefix)) {
            return item;
        }
        return new TurAssetItem(item.name().substring(prefix.length()),
                item.size(), item.contentType(), item.lastModified(), item.directory());
    }

    private List<TurAssetItem> stripPrefix(List<TurAssetItem> items) {
        String prefix = currentPrefix();
        if (prefix.isEmpty()) {
            return items;
        }
        return items.stream().map(i -> stripPrefix(i, prefix)).toList();
    }

    @Override
    public TurStorageType getType() {
        return delegate.getType();
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    @Override
    public List<TurAssetItem> listObjects(String prefix) {
        return stripPrefix(delegate.listObjects(scopeKey(prefix)));
    }

    @Override
    public List<TurAssetItem> listAllObjects() {
        String prefix = currentPrefix();
        if (prefix.isEmpty()) {
            return delegate.listAllObjects();
        }
        // Scope an "all" listing to the tenant root, then strip the prefix back off.
        return stripPrefix(delegate.listObjects(prefix));
    }

    @Override
    public InputStream downloadObject(String objectName) {
        return delegate.downloadObject(scopeKey(objectName));
    }

    @Override
    public TurStorageObjectStat statObject(String objectName) {
        return delegate.statObject(scopeKey(objectName));
    }

    @Override
    public void uploadObject(MultipartFile file, String prefix) {
        delegate.uploadObject(file, scopeKey(prefix));
    }

    @Override
    public void createFolder(String folderPath) {
        delegate.createFolder(scopeKey(folderPath));
    }

    @Override
    public void uploadStream(String objectName, InputStream inputStream, long size, String contentType) {
        delegate.uploadStream(scopeKey(objectName), inputStream, size, contentType);
    }

    @Override
    public void deleteObjectsWithPrefix(String prefix) {
        delegate.deleteObjectsWithPrefix(scopeKey(prefix));
    }

    @Override
    public void deleteObject(String objectName) {
        delegate.deleteObject(scopeKey(objectName));
    }
}
