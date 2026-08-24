import { react } from '@viglet/eslint-config/react'

/**
 * T1025 — the headless UI package. React, published, and constrained to zero
 * runtime dependencies — the rules are the shared ones.
 */
export default [
  ...react(),
]
