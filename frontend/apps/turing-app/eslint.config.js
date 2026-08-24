import { react } from '@viglet/eslint-config/react'

/**
 * T1025 — the severities moved to `@viglet/eslint-config`, which is where the
 * argument for each of them now lives. This file keeps only what is true of
 * this app and of nothing else.
 */
export default [
  ...react(),

  // T1024 — a route table is not a component module.
  //
  // `*.routes.tsx` exports one JSX `<Route>` tree and declares the lazy page
  // constants that tree points at. Fast Refresh's rule is about preserving
  // component state across an edit, and there is no state to preserve in a
  // route table: editing it remounts the tree either way. Left on, the rule
  // reported 139 findings in one file — every lazy import — which was most of
  // what stood under this app's warning ceiling and none of it actionable.
  {
    files: ['src/app/routes/*.routes.tsx'],
    rules: { 'react-refresh/only-export-components': 'off' },
  },
]
