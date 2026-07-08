/**
 * Public library surface of {@code @viglet/turing-cli}. The CLI itself lives in
 * {@code cli.ts}; these exports let other tools embed the pieces (e.g. run an
 * eval suite from a custom script).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

export { TuringClient, HttpError, consumeSse, type TuringAuth, type SseEvent } from "./client.js";
export {
  loadProject,
  resolveConnection,
  promptPassword,
  type ProjectConfig,
  type Connection,
  type ConnectionFlags,
  type LoadedProject,
} from "./config.js";
export { scaffoldFiles } from "./scaffold.js";
export { buildZip, crc32, type ZipEntry } from "./zip.js";

export { parseYaml, YamlError } from "./eval/yaml.js";
export { loadSuiteFile, normalizeSuite, EvalParseError } from "./eval/load.js";
export { runSuite, evaluate, describeAssertion, type EvalBackend, type AuditEntry } from "./eval/runner.js";
export { ClientEvalBackend } from "./eval/backend.js";
export { buildFixtureYaml, recordFixture, type ConversationExport } from "./eval/record.js";
export {
  runEval,
  formatRunResult,
  type EvalRunResult,
  type EvalReport,
  type EvalCaseResult,
  type RunEvalOptions,
} from "./eval/remote.js";
export type * from "./eval/types.js";

export { deployProject, buildFlowImportPayload, buildToolPayload } from "./commands/deploy.js";
export { discoverSuiteFiles, runEvalFiles, formatSuiteDetail } from "./commands/eval.js";
export { scaffoldFiles as initScaffold } from "./scaffold.js";
