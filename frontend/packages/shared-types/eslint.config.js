import globals from 'globals'
import { library } from '@viglet/eslint-config/library'

/**
 * T1025 — 42 lines of types and no code, which is exactly why it is
 * cheap to hold to the same rules as everything else.
 */
export default [
  ...library({ globals: globals.node }),
]
