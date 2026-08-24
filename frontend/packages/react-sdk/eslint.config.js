import { react } from '@viglet/eslint-config/react'

/**
 * T1025 — the SDK's own hooks and components are React, and are imported by
 * consumers rather than by a page we control — so the rules that hold in the
 * app hold here with more force, not less.
 */
export default [
  ...react(),

  // A story simulates. `Math.random() < failProbability` is how the flow-chooser
  // and slot-writer stories show what a 500 looks like, and `Date.now()` is how
  // the chat story mints a turn id — both inside functions a click calls, never
  // during render. `react-hooks/purity` cannot prove that about a function
  // declared in a component body, so it reports all three as impure renders.
  //
  // The scope is deliberately `src/stories`: `tsconfig.json` already excludes
  // that directory from what is published (T915), so nothing switched off here
  // reaches a consumer's bundle. Everywhere else in this package the rule is an
  // error, which is what it should be.
  {
    files: ['src/stories/**/*.{ts,tsx}', 'src/**/*.stories.tsx'],
    rules: { 'react-hooks/purity': 'off' },
  },
]
