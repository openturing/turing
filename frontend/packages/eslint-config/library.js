import globals from 'globals'
import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import { ignores, baseRules } from './index.js'

/**
 * The two environments a published client can be loaded in at once. Exported so
 * a package that needs it does not have to depend on `globals` itself — the SDK
 * is zero-dependency by design and its manifest should keep saying so.
 */
export const browserAndNode = { ...globals.browser, ...globals.node }

/**
 * T1025 — the entry point for a package that ships no React.
 *
 * `@viglet/turing-sdk` is the case this exists for: vanilla, zero-dependency,
 * and built to run inside an Adobe EDS block or a plain `<script>`. Loading the
 * React plugins there would report nothing and still make the config lie about
 * what the package is.
 *
 * @param {{ globals?: Record<string, boolean> }} [options]
 */
export function library(options = {}) {
  return tseslint.config([
    ignores,
    {
      files: ['**/*.{ts,mts,cts}'],
      extends: [js.configs.recommended, tseslint.configs.recommended],
      languageOptions: {
        ecmaVersion: 2020,
        globals: options.globals ?? globals.browser,
      },
      rules: baseRules,
    },
  ])
}

export default library
