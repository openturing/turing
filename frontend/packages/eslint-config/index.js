import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import { globalIgnores } from 'eslint/config'

/**
 * T1025 — the severities, once, for every package in the monorepo.
 *
 * This is the base every other entry point builds on: the TypeScript rules and
 * the one convention this codebase already had before any of them, which is
 * that a leading underscore means "this exists because the signature has it".
 *
 * The severities are the ones T1016 arrived at, and the shape of that decision
 * is worth keeping in front of whoever edits this file. An **error** is
 * something no commit may add. A **warning** is something no commit may add
 * *more of* — every package runs `lint` with `--max-warnings` pinned to its own
 * measured count, so the two differ in what it costs to clear the backlog, not
 * in whether the line holds. A rule is demoted when its backlog is a reading of
 * many call sites rather than a mechanical edit. None is switched off to make a
 * number smaller.
 */
export const ignores = globalIgnores([
  'dist',
  'build',
  'storybook-static',
  'coverage',
  '**/*.d.ts',
])

/** The rules that hold everywhere, app or library, browser or not. */
export const baseRules = {
  // A leading underscore is this codebase's existing way of saying "this
  // parameter exists because the signature has it". Every finding T1016 read
  // was already written that way, so the convention was the rule's and not the
  // code's.
  '@typescript-eslint/no-unused-vars': ['error', {
    argsIgnorePattern: '^_',
    varsIgnorePattern: '^_',
    caughtErrorsIgnorePattern: '^_',
  }],
}

/** The TypeScript baseline, with no assumption about React or the browser. */
export const typescript = [
  ignores,
  {
    files: ['**/*.{ts,tsx,mts,cts}'],
    extends: [js.configs.recommended, tseslint.configs.recommended],
    languageOptions: { ecmaVersion: 2020 },
    rules: baseRules,
  },
]

export default typescript
