import globals from 'globals'
import { library } from '@viglet/eslint-config/library'

/**
 * T1025 — the published chatflow DSL. A parser and a validator — node
 * globals, nothing rendered.
 */
export default [
  ...library({ globals: globals.node }),
]
