import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import js from '@eslint/js'
import { ignores, baseRules } from './index.js'

/**
 * T1025 — the React severities, shared.
 *
 * Everything here was argued once, in `apps/turing-app`, against a real backlog
 * of 228 findings. What follows is that argument, not a copy of a preset.
 */
export const reactRules = {
  ...baseRules,

  // Fast Refresh, not correctness: a module exporting both a component and
  // something else reloads whole instead of preserving state, so editing it
  // during development loses whatever was on screen. Never a runtime bug, which
  // is why it is a warning; the fix for each finding is deciding what the module
  // should have been split into, which is a design question per file.
  'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],

  // The React Compiler rules, which arrived as errors with react-hooks 7 on code
  // written before them. Worth keeping and worth reading — `set-state-in-effect`
  // is the pattern this repository already has a written rule about, arrived at
  // from a real bug — but a rewrite of an effect is where regressions live, so
  // they warn rather than block.
  'react-hooks/set-state-in-effect': 'warn',
  'react-hooks/refs': 'warn',
  'react-hooks/static-components': 'warn',
  'react-hooks/immutability': 'warn',

  // Off, and the reason is that no package here runs the React Compiler: there
  // is no `babel-plugin-react-compiler` and no vite config turns one on. These
  // two report only what the compiler would decline to memoise, so with it
  // switched off they name an optimization that is not happening.
  //
  // The cost of obeying them anyway is the argument. In `turing-app` every one
  // of the 25 `incompatible-library` findings was react-hook-form's
  // `form.watch()`; the fix is `useWatch`, which is 81 call sites across 31
  // forms, and 38 of those sit inside JSX, a callback or a `.map()` where a hook
  // cannot go at all.
  //
  // Turn these back on in the same change that adds the compiler, not before —
  // they are how you would find the components it bails out of.
  'react-hooks/preserve-manual-memoization': 'off',
  'react-hooks/incompatible-library': 'off',
}

/**
 * @param {{ globals?: Record<string, boolean> }} [options]
 *   `globals` replaces the browser set — a package that also runs in node
 *   passes `{...globals.browser, ...globals.node}`.
 */
export function react(options = {}) {
  return tseslint.config([
    ignores,
    {
      files: ['**/*.{ts,tsx}'],
      extends: [
        js.configs.recommended,
        tseslint.configs.recommended,
        // `configs['recommended-latest']` is the eslintrc shape — its `plugins`
        // is the array `["react-hooks"]`, which ESLint 10 rejects outright. The
        // `flat` namespace is the same 17 rules with `plugins` as an object.
        reactHooks.configs.flat['recommended-latest'],
        reactRefresh.configs.vite,
      ],
      languageOptions: {
        ecmaVersion: 2020,
        globals: options.globals ?? globals.browser,
      },
      rules: reactRules,
    },
  ])
}

export default react
