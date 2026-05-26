#!/usr/bin/env node
/**
 * `turing-flow-dsl build <input-dir> [--out <output-dir>]`
 *
 * <p>Zero-dep CLI that scans an input directory for chat-flow sources
 * and emits the editor's JSON wire shape into the output directory.
 *
 * <p>Input file types:
 * <ul>
 *   <li>{@code *.flow.json} — back-compat raw export from the editor; the
 *       file is parsed, lightly normalised through {@link transpileFlow}'s
 *       sister path (it's already in the wire shape, so it's copied
 *       through verbatim), and written under the same base name with
 *       a {@code .chat-flow.json} extension. This satisfies the T98
 *       "back-compat reading raw JSON" requirement so a team can mix
 *       hand-authored JSON and DSL TS files in the same {@code flows/}
 *       directory.</li>
 *   <li>{@code *.flow.mjs} / {@code *.flow.js} — ES module compiled from
 *       a {@code *.flow.ts} source (run {@code tsc} first). The CLI
 *       dynamically imports the file; the default export (or a
 *       {@code flow}/{@code flows} named export) must be a
 *       {@link FlowSpec} or {@code FlowSpec[]}, which the CLI runs
 *       through {@link transpileFlow}.</li>
 * </ul>
 *
 * <p>Output: each input becomes {@code <basename>.chat-flow.json} in the
 * output directory (default {@code ./dist}), ready to drop on the editor's
 * "Import JSON" button or feed to {@code POST /chat-flow/import}.
 *
 * <p>Exit codes: {@code 0} success, {@code 1} on validation or IO error.
 * The CLI prints one line per file plus a summary at the end so it slots
 * into CI / npm scripts naturally.
 *
 * @since 2026.3.1
 */
/**
 * Walks {@code inputDir}, processes every matching file, writes the
 * transpiled JSON into {@code outputDir}. Returns the count of files
 * processed and failed so the test harness can drive it without spawning
 * a real process.
 */
export declare function build(inputDir: string, outputDir: string): Promise<{
    ok: number;
    failed: number;
}>;
//# sourceMappingURL=cli.d.ts.map