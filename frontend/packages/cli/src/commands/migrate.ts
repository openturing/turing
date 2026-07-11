/**
 * {@code turing migrate elasticsearch|algolia} — the real "step 2" of switching
 * to Turing (Block AO / T659). Wraps the backend migration seams (T657/T658):
 * point the CLI at a source index and it derives the field manifest, provisions
 * the SN site, and imports every record — one command instead of an ETL script.
 *
 * <p>The backend does all the work (reads the source, provisions, imports); the
 * CLI just POSTs the connection details through the shared {@code TuringClient}
 * and prints the result. {@code --dry-run} returns the derived manifest without
 * provisioning or importing, so the schema can be reviewed first.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import type { TuringClient } from "../client.js";

/** Engines the importer supports. */
export type MigrateEngine = "elasticsearch" | "algolia";

/** Mirrors the backend {@code TurMigrationResult}. */
export interface MigrationResult {
  engine: string;
  site: string;
  siteCreated: boolean;
  fieldsDetected: number;
  fieldsCreated: string[];
  fieldsSkipped: string[];
  documentsRead: number;
  documentsImported: number;
  dryRun: boolean;
  manifest?: { fields?: Array<{ name: string; type: string; facet?: boolean; multiValued?: boolean }> };
  warnings: string[];
}

/** Raw parsed `--flag value` map from the CLI (values are string | boolean). */
export type MigrateFlags = Record<string, string | boolean>;

function str(flags: MigrateFlags, key: string): string | undefined {
  const v = flags[key];
  return typeof v === "string" ? v : undefined;
}

function int(flags: MigrateFlags, key: string): number | undefined {
  const v = str(flags, key);
  if (v === undefined) return undefined;
  const n = Number(v);
  if (!Number.isFinite(n)) throw new Error(`--${key} must be a number`);
  return n;
}

function required(value: string | undefined, flag: string): string {
  if (!value) throw new Error(`Missing required --${flag}. Run \`turing migrate\` for usage.`);
  return value;
}

/** Builds the `/api/sn/migrate/{engine}` request body from the parsed flags. */
export function buildMigrateBody(
  engine: MigrateEngine,
  flags: MigrateFlags,
  overrides?: unknown[],
): Record<string, unknown> {
  const common = {
    index: required(str(flags, "index"), "index"),
    targetSite: required(str(flags, "site"), "site"),
    seInstanceId: str(flags, "se-instance"),
    locale: str(flags, "locale"),
    batchSize: int(flags, "batch-size"),
    maxDocuments: int(flags, "max-documents"),
    dryRun: flags["dry-run"] === true,
    overrides: overrides && overrides.length > 0 ? overrides : undefined,
  };
  if (engine === "elasticsearch") {
    return {
      ...common,
      sourceUrl: required(str(flags, "source-url"), "source-url"),
      username: str(flags, "source-user"),
      password: str(flags, "source-password"),
      apiKey: str(flags, "source-api-key"),
    };
  }
  return {
    ...common,
    appId: required(str(flags, "app-id"), "app-id"),
    apiKey: required(str(flags, "api-key"), "api-key"),
    host: str(flags, "host"),
    useLlm: flags["use-llm"] === true,
  };
}

/** Authenticates, POSTs the migration request, and prints the result. */
export async function runMigrate(
  client: TuringClient,
  engine: MigrateEngine,
  flags: MigrateFlags,
  log: (line: string) => void,
  overrides?: unknown[],
): Promise<MigrationResult> {
  const body = buildMigrateBody(engine, flags, overrides);
  await client.authenticate();
  log(`Migrating ${engine} index '${body.index}' → SN site '${body.targetSite}'${body.dryRun ? " (dry run)" : ""}…`);
  const result = await client.post<MigrationResult>(`/api/sn/migrate/${engine}`, body);
  for (const line of formatMigrationResult(result)) log(line);
  return result;
}

/* ─────────────────────── shadow comparison (T661) ─────────────────────── */

/** Mirrors the backend {@code TurMigrationCompareMetrics.QueryComparison}. */
export interface QueryComparison {
  query: string;
  sourceCount: number;
  turingCount: number;
  sharedCount: number;
  jaccard: number;
  sourceRecall: number;
  topRankMatch: boolean;
}

/** Mirrors the backend {@code TurMigrationCompareResult}. */
export interface CompareResult {
  engine: string;
  site: string;
  queries: number;
  rows: number;
  avgJaccard: number;
  avgSourceRecall: number;
  topRankMatches: number;
  zeroOverlapQueries: number;
  perQuery: QueryComparison[];
  warnings: string[];
}

/** Builds the `/api/sn/migrate/compare` request body from flags + a query set. */
export function buildCompareBody(flags: MigrateFlags, queries: string[]): Record<string, unknown> {
  if (queries.length === 0) throw new Error("No queries to compare. Provide --queries-file <path>.");
  return {
    engine: required(str(flags, "engine"), "engine"),
    sourceUrl: str(flags, "source-url"),
    username: str(flags, "source-user"),
    password: str(flags, "source-password"),
    apiKey: str(flags, "source-api-key") ?? str(flags, "api-key"),
    appId: str(flags, "app-id"),
    host: str(flags, "host"),
    index: required(str(flags, "index"), "index"),
    targetSite: required(str(flags, "site"), "site"),
    locale: str(flags, "locale"),
    rows: int(flags, "rows"),
    queries,
  };
}

/** Authenticates, POSTs the comparison request, and prints the parity report. */
export async function runCompare(
  client: TuringClient,
  flags: MigrateFlags,
  queries: string[],
  log: (line: string) => void,
): Promise<CompareResult> {
  const body = buildCompareBody(flags, queries);
  await client.authenticate();
  log(`Comparing ${queries.length} query(ies): ${body.engine} '${body.index}' vs SN site '${body.targetSite}'…`);
  const result = await client.post<CompareResult>("/api/sn/migrate/compare", body);
  for (const line of formatCompareResult(result)) log(line);
  return result;
}

/** Human-readable relevance-parity report. */
export function formatCompareResult(result: CompareResult): string[] {
  const pct = (n: number) => `${Math.round(n * 100)}%`;
  const lines = [
    `\n${result.engine} vs site '${result.site}' — ${result.queries} query(ies), top ${result.rows}`,
    `  avg overlap (Jaccard): ${pct(result.avgJaccard)}`,
    `  avg source recall:     ${pct(result.avgSourceRecall)}`,
    `  top-1 matches:         ${result.topRankMatches}/${result.queries}`,
    `  zero-overlap queries:  ${result.zeroOverlapQueries}/${result.queries}`,
    "",
  ];
  for (const q of result.perQuery) {
    lines.push(`  "${q.query}" — overlap ${pct(q.jaccard)}, shared ${q.sharedCount}${q.topRankMatch ? ", top-1 ✓" : ""}`);
  }
  if (result.warnings?.length) {
    lines.push(`\n⚠ ${result.warnings.length} warning(s):`);
    for (const w of result.warnings) lines.push(`  - ${w}`);
  }
  return lines;
}

/** Human-readable summary of a migration result. */
export function formatMigrationResult(result: MigrationResult): string[] {
  const body = result.dryRun ? dryRunLines(result) : realRunLines(result);
  return [...body, ...warningLines(result.warnings)];
}

function dryRunLines(result: MigrationResult): string[] {
  const fields = (result.manifest?.fields ?? []).map((f) => {
    const flags = [f.facet ? "facet" : null, f.multiValued ? "multi" : null].filter(Boolean).join(", ");
    return `  • ${f.name}: ${f.type}${flags ? " (" + flags + ")" : ""}`;
  });
  return [`\nDry run — ${result.fieldsDetected} field(s) detected (nothing provisioned or imported):`, ...fields];
}

function realRunLines(result: MigrationResult): string[] {
  return [
    `\n✓ ${result.engine} → site '${result.site}'${result.siteCreated ? " (created)" : " (converged)"}`,
    `  fields: ${result.fieldsCreated.length} created, ${result.fieldsSkipped.length} existed`,
    `  documents: ${result.documentsImported} queued for indexing`,
  ];
}

function warningLines(warnings: string[]): string[] {
  if (!warnings?.length) return [];
  return [`\n⚠ ${warnings.length} warning(s):`, ...warnings.map((w) => `  - ${w}`)];
}
