import { react } from '@viglet/eslint-config/react'

/**
 * T1025 — the headless UI package. React, published, and constrained to zero
 * runtime dependencies — the rules are the shared ones.
 */
export default [
  ...react(),

  // T1027 — off for this package, and the reason is what the package is.
  //
  // `package.json` publishes a subpath per module: `@viglet/turing-react-ui/
  // TuringRichContent` exports both the component and `splitRichContent`, and
  // `check-exports.mjs` holds that surface against a baseline. Twenty of this
  // package's twenty-two findings are a pure helper exported beside the
  // component it belongs to, which is exactly the shape those subpaths promise.
  // Splitting them would move an export out of the module a consumer imports it
  // from — a breaking change, made to serve a dev-server nobody runs against
  // this package, since consumers import `dist`.
  //
  // The rule stays on in the apps, where Fast Refresh is a thing that happens.
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: { 'react-refresh/only-export-components': 'off' },
  },
]
