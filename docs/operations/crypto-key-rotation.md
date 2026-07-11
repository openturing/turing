# AI crypto master-key rotation

Turing encrypts every stored provider API key and store credential with a single
AES/GCM master key derived from `turing.ai.crypto.key`
(env var `TURING_AI_CRYPTO_KEY`). This document is the rotation procedure
referenced by `TurCryptoKeyMaterial` (T639 / IMPROVEMENTS §XXXVII.1).

## Rules

- **Never commit a real key.** `application.yaml` ships the property as
  `ai.crypto.key: ${TURING_AI_CRYPTO_KEY:}` — a blank default. Under the
  `production` profile a blank key **fails fast at startup**, and a known
  sample/dev sentinel (e.g. the historical `sample-key-for-crypto`) is
  **rejected** — see `TurCryptoKeyMaterial.REJECTED_SENTINELS`.
- Supply the key from the environment only: `TURING_AI_CRYPTO_KEY=<strong-random-value>`.
  Use a long, high-entropy value (e.g. `openssl rand -base64 48`).
- Outside production a warned-once insecure dev fallback is used, so local dev
  needs no key.

## Why rotation matters

Because the sample key was committed for a period, any secret encrypted while it
was active is compromised: anyone with repo access + a database copy could
decrypt it. Rotating the key alone does **not** re-secure those rows — the old
ciphertext is still decryptable with the old (public) key until it is
re-encrypted or the underlying secret is itself rotated.

## Recommended procedure

Prefer **rotating the underlying secrets** over re-encrypting old ciphertext,
because the plaintext provider keys were themselves exposed:

1. **Rotate at the source.** For each LLM/embedding/rerank provider, store
   credential, integration, webhook auth header, and MCP client secret, issue a
   **new** credential in the provider's console and revoke the old one.
2. **Set the new master key.** Put a fresh `TURING_AI_CRYPTO_KEY` in the
   environment/secret manager.
3. **Re-enter the rotated secrets** through the admin UI (the write-only
   `@Transient` fields re-encrypt on save under the new key).
4. **Restart** the app so the production fail-fast validates the new key.

### Cross-container / multi-instance note (T373)

All instances that share a database must share the **same** master key, or
ciphertext written by one instance cannot be decrypted by another. Roll the env
var out to every instance in the same deploy before restarting any of them.

### Bulk re-encryption (alternative, when source rotation is impractical)

If the provider credentials genuinely cannot be reissued, decrypt each stored
secret with the **old** key and re-encrypt with the **new** key in a one-off
maintenance job (old key configured, read + decrypt all rows, swap key, encrypt
+ persist). This still leaves the previously-exposed plaintext valid, so treat
it as a stopgap and schedule source rotation.
