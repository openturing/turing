---
name: turing-storage
description: Turing pluggable storage layer — the TurStorageService abstraction and its three implementations (NONE / MINIO / FILESYSTEM), configuration under turing.storage.*, the key classes in service/storage/, the property classes, and the storageEnabled frontend feature flag. Use whenever working with asset/file storage, MinIO, filesystem storage, uploads/downloads, or the storage config.
---

# Pluggable Storage Architecture

The storage layer is abstracted via `TurStorageService` interface with three implementations:

**Configuration** (`application.yaml`):

```yaml
turing:
  storage:
    type: none # none | minio | filesystem
    filesystem:
      path: ./store/assets
    minio:
      endpoint: http://localhost:9000
      access-key: admin
      secret-key: minha_senha_forte
      bucket: turing-assets
```

**Key classes** (all in `service/storage/`):

- `TurStorageType` — enum: `NONE`, `MINIO`, `FILESYSTEM`
- `TurStorageService` — interface: `isEnabled()`, `getType()`, `listObjects()`, `downloadObject()`, `statObject()`, `uploadObject()`, `uploadStream()`, `deleteObject()`, `deleteObjectsWithPrefix()`, `createFolder()`, `guessContentType()`
- `TurStorageObjectStat` — neutral DTO replacing MinIO's `StatObjectResponse`
- `TurStorageContentTypes` — shared static MIME type utility
- `TurMinioStorageService` — MinIO implementation
- `TurFilesystemStorageService` — filesystem implementation with path traversal protection (`resolveSafe()`)
- `TurNoOpStorageService` — disabled/no-op implementation (lists return empty, mutations throw)
- `TurStorageConfig` — `@Configuration` with `@Bean` factory that selects implementation based on `turing.storage.type`

**Properties** (in `properties/`):

- `TurStorageProperty` — `type` (enum), nested `minio` (TurMinioProperty), nested `filesystem` (TurFilesystemProperty with `path`)
- `TurMinioProperty` — `endpoint`, `accessKey`, `secretKey`, `bucket` (no `enabled` field — type selection controls this)
- Access pattern: `configProperties.getStorage().getMinio()` or `configProperties.getStorage().getFilesystem().getPath()`
- Old `turing.minio.enabled` and `turing.minio.*` are **removed** — all storage config lives under `turing.storage.*`

**Frontend feature flag**: `FeaturesResponse.storageEnabled` (renamed from `minioEnabled`)

- Sidebar hides "assets" and "pages" when `storageEnabled=false`
- System Info shows storage section with dynamic type label and property key
