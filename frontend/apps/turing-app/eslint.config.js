import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { globalIgnores } from 'eslint/config'

export default tseslint.config([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      // `configs['recommended-latest']` is the eslintrc shape — its `plugins`
      // is the array `["react-hooks"]`, which ESLint 10 rejects outright, so
      // `eslint .` failed before it linted a file. The `flat` namespace is the
      // same 17 rules with `plugins` as an object.
      reactHooks.configs.flat['recommended-latest'],
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    // T1016 — the severities below are the ones this app does not inherit.
    //
    // The shape of the decision: an error is something no commit may add, and a
    // warning is something no commit may add *more of* — `lint` runs with
    // `--max-warnings` pinned to today's count, so the two differ in what it
    // costs to fix the backlog, not in whether the line holds. Every rule
    // demoted here is demoted because its backlog is a reading of many call
    // sites rather than a mechanical edit, and none is switched off.
    rules: {
      // A leading underscore is this codebase's existing way of saying "this
      // parameter exists because the signature has it". Every one of the eight
      // findings was already written that way, so the convention was the rule's
      // and not the code's.
      '@typescript-eslint/no-unused-vars': ['error', {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
        caughtErrorsIgnorePattern: '^_',
      }],

      // Fast Refresh, not correctness: a module exporting both a component and
      // something else reloads the whole module instead of preserving state.
      // 188 findings over 33 files, and the fix for each is deciding what the
      // module should have been split into — a design question per file, and
      // never a runtime bug.
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],

      // The React Compiler rules, which arrived as errors with react-hooks 7 on
      // a codebase written before them. They are worth keeping and worth
      // reading: `set-state-in-effect` alone is 76 findings over 63 files, and
      // this repository already has a written rule about the pattern in
      // agents.md, arrived at from a real bug. What it does not have is 63 files
      // of verified rewrites, and a rewrite of an effect is where regressions
      // live.
      'react-hooks/set-state-in-effect': 'warn',
      'react-hooks/refs': 'warn',
      'react-hooks/static-components': 'warn',
      'react-hooks/immutability': 'warn',
      'react-hooks/preserve-manual-memoization': 'warn',
      'react-hooks/incompatible-library': 'warn',
    },
  },
])
