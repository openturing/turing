import globals from 'globals'
import { library } from '@viglet/eslint-config/library'

/**
 * T1025 — a published node CLI: no browser, no React, and no DOM globals to
 * pretend it has.
 */
export default [
  ...library({ globals: globals.node }),

  // A test reaches into a shape its public type does not expose — the import
  // payload's inner fields, a parsed YAML document, the body a stubbed client
  // was called with. `as any` is how it says "I know what this is and the
  // signature does not"; the alternative is a cast chain that asserts the same
  // thing at more length. All seven findings were in `test/`, none in `src/`,
  // which is the reason this is scoped rather than switched off.
  {
    files: ['test/**/*.ts'],
    rules: { '@typescript-eslint/no-explicit-any': 'off' },
  },
]
