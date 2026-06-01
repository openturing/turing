import { defineConfig } from "vite";
import { resolve } from "path";

/**
 * Library build for `@viglet/turing-sdk`. Mirrors the lib-mode setup used by
 * `shio-js-sdk`: emits a self-contained ESM bundle (the file an Adobe EDS repo
 * vendors into `scripts/` or imports from a CDN) plus a UMD build for
 * `<script>`/bundler consumers. Type declarations are emitted separately by
 * `tsc --emitDeclarationOnly` (see package.json `build` script) so the bundle
 * stays free of any external runtime dependency.
 *
 * @since 2026.3.1
 */
export default defineConfig({
  build: {
    lib: {
      entry: resolve(__dirname, "src/index.ts"),
      name: "TuringSDK",
      formats: ["es", "umd"],
      fileName: (format) =>
        format === "umd" ? "turing-sdk.umd.cjs" : "turing-sdk.js",
    },
    outDir: "dist",
    emptyOutDir: true,
    minify: "esbuild",
    sourcemap: true,
  },
});
