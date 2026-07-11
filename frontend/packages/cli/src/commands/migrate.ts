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
export function buildMigrateBody(engine: MigrateEngine, flags: MigrateFlags): Record<string, unknown> {
  const common = {
    index: required(str(flags, "index"), "index"),
    targetSite: required(str(flags, "site"), "site"),
    seInstanceId: str(flags, "se-instance"),
    locale: str(flags, "locale"),
    batchSize: int(flags, "batch-size"),
    maxDocuments: int(flags, "max-documents"),
    dryRun: flags["dry-run"] === true,
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
): Promise<MigrationResult> {
  const body = buildMigrateBody(engine, flags);
  await client.authenticate();
  log(`Migrating ${engine} index '${body.index}' → SN site '${body.targetSite}'${body.dryRun ? " (dry run)" : ""}…`);
  const result = await client.post<MigrationResult>(`/api/sn/migrate/${engine}`, body);
  for (const line of formatMigrationResult(result)) log(line);
  return result;
}

/** Human-readable summary of a migration result. */
export function formatMigrationResult(result: MigrationResult): string[] {
  const lines: string[] = [];
  if (result.dryRun) {
    lines.push(`\nDry run — ${result.fieldsDetected} field(s) detected (nothing provisioned or imported):`);
    for (const f of result.manifest?.fields ?? []) {
      const flags = [f.facet ? "facet" : null, f.multiValued ? "multi" : null].filter(Boolean).join(", ");
      lines.push(`  • ${f.name}: ${f.type}${flags ? " (" + flags + ")" : ""}`);
    }
  } else {
    lines.push(`\n✓ ${result.engine} → site '${result.site}'${result.siteCreated ? " (created)" : " (converged)"}`);
    lines.push(`  fields: ${result.fieldsCreated.length} created, ${result.fieldsSkipped.length} existed`);
    lines.push(`  documents: ${result.documentsImported} queued for indexing`);
  }
  if (result.warnings?.length) {
    lines.push(`\n⚠ ${result.warnings.length} warning(s):`);
    for (const w of result.warnings) lines.push(`  - ${w}`);
  }
  return lines;
}
