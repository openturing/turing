import { test } from "node:test";
import assert from "node:assert/strict";

import { buildMigrateBody, formatMigrationResult, type MigrationResult } from "../src/commands/migrate.js";

/* ── buildMigrateBody (T659) ── */

test("buildMigrateBody builds the Elasticsearch request from flags", () => {
  const body = buildMigrateBody("elasticsearch", {
    "source-url": "https://es:9200",
    "source-user": "elastic",
    "source-password": "secret",
    index: "products",
    site: "Products",
    "se-instance": "se-1",
    locale: "en_US",
    "batch-size": "250",
    "dry-run": true,
  });
  assert.equal(body.sourceUrl, "https://es:9200");
  assert.equal(body.username, "elastic");
  assert.equal(body.password, "secret");
  assert.equal(body.index, "products");
  assert.equal(body.targetSite, "Products");
  assert.equal(body.seInstanceId, "se-1");
  assert.equal(body.batchSize, 250);
  assert.equal(body.dryRun, true);
});

test("buildMigrateBody builds the Algolia request and honors --use-llm", () => {
  const body = buildMigrateBody("algolia", {
    "app-id": "APP1",
    "api-key": "KEY1",
    index: "catalog",
    site: "Catalog",
    "use-llm": true,
  });
  assert.equal(body.appId, "APP1");
  assert.equal(body.apiKey, "KEY1");
  assert.equal(body.index, "catalog");
  assert.equal(body.targetSite, "Catalog");
  assert.equal(body.useLlm, true);
  assert.equal(body.dryRun, false);
});

test("buildMigrateBody requires the mandatory flags", () => {
  assert.throws(() => buildMigrateBody("elasticsearch", { index: "i", site: "s" }), /source-url/);
  assert.throws(() => buildMigrateBody("elasticsearch", { "source-url": "u", site: "s" }), /index/);
  assert.throws(() => buildMigrateBody("algolia", { "api-key": "k", index: "i", site: "s" }), /app-id/);
});

test("buildMigrateBody rejects a non-numeric --batch-size", () => {
  assert.throws(
    () => buildMigrateBody("algolia", { "app-id": "a", "api-key": "k", index: "i", site: "s", "batch-size": "lots" }),
    /batch-size must be a number/,
  );
});

/* ── formatMigrationResult ── */

test("formatMigrationResult lists fields on a dry run", () => {
  const result: MigrationResult = {
    engine: "ELASTICSEARCH", site: "Products", siteCreated: false,
    fieldsDetected: 2, fieldsCreated: [], fieldsSkipped: [],
    documentsRead: 5, documentsImported: 0, dryRun: true,
    manifest: { fields: [{ name: "title", type: "TEXT" }, { name: "brand", type: "STRING", facet: true }] },
    warnings: [],
  };
  const out = formatMigrationResult(result).join("\n");
  assert.match(out, /Dry run/);
  assert.match(out, /title: TEXT/);
  assert.match(out, /brand: STRING \(facet\)/);
});

test("formatMigrationResult summarizes a real run with warnings", () => {
  const result: MigrationResult = {
    engine: "ALGOLIA", site: "Catalog", siteCreated: true,
    fieldsDetected: 3, fieldsCreated: ["a", "b"], fieldsSkipped: ["c"],
    documentsRead: 100, documentsImported: 100, dryRun: false,
    warnings: ["2 Algolia synonym set(s) found."],
  };
  const out = formatMigrationResult(result).join("\n");
  assert.match(out, /site 'Catalog' \(created\)/);
  assert.match(out, /2 created, 1 existed/);
  assert.match(out, /100 queued/);
  assert.match(out, /1 warning/);
});
