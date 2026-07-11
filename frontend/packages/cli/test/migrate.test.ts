import { test } from "node:test";
import assert from "node:assert/strict";

import {
  buildCompareBody,
  buildMigrateBody,
  formatCompareResult,
  formatMigrationResult,
  type CompareResult,
  type MigrationResult,
} from "../src/commands/migrate.js";

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

test("buildMigrateBody includes overrides when provided, omits when empty", () => {
  const overrides = [{ field: "cost", rename: "price", type: "CURRENCY" }];
  const withOv = buildMigrateBody("elasticsearch", { "source-url": "u", index: "i", site: "s" }, overrides);
  assert.deepEqual(withOv.overrides, overrides);

  const withoutOv = buildMigrateBody("elasticsearch", { "source-url": "u", index: "i", site: "s" }, []);
  assert.equal(withoutOv.overrides, undefined);
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

/* ── compare (T661) ── */

test("buildCompareBody carries the query set and resolves the source api key", () => {
  const body = buildCompareBody(
    { engine: "algolia", "app-id": "A", "api-key": "K", index: "i", site: "s", rows: "20" },
    ["tv", "laptop"],
  );
  assert.equal(body.engine, "algolia");
  assert.equal(body.appId, "A");
  assert.equal(body.apiKey, "K");
  assert.equal(body.rows, 20);
  assert.deepEqual(body.queries, ["tv", "laptop"]);
});

test("buildCompareBody requires at least one query", () => {
  assert.throws(() => buildCompareBody({ engine: "algolia", index: "i", site: "s" }, []), /No queries/);
});

test("formatCompareResult renders the parity report as percentages", () => {
  const result: CompareResult = {
    engine: "ALGOLIA", site: "Catalog", queries: 2, rows: 10,
    avgJaccard: 0.75, avgSourceRecall: 0.9, topRankMatches: 1, zeroOverlapQueries: 0,
    perQuery: [
      { query: "tv", sourceCount: 5, turingCount: 5, sharedCount: 4, jaccard: 0.8, sourceRecall: 0.8, topRankMatch: true },
    ],
    warnings: [],
  };
  const out = formatCompareResult(result).join("\n");
  assert.match(out, /avg overlap \(Jaccard\): 75%/);
  assert.match(out, /top-1 matches:\s+1\/2/);
  assert.match(out, /"tv" — overlap 80%, shared 4, top-1 ✓/);
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
