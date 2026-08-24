import { react } from '@viglet/eslint-config/react'

/**
 * T1025 — the SDK's own hooks and components are React, and are imported by
 * consumers rather than by a page we control — so the rules that hold in the
 * app hold here with more force, not less.
 */
/**
 * Where the per-file `eslint-disable` rationales live, and why they live here.
 *
 * T1027 wrote its justification into the file it suppressed — the honest place
 * for it, except that `tsc` emits comments into `dist`, and `files: ["dist/**"]`
 * publishes them. The same 736-byte paragraph on 14 hooks became 74 kB of prose
 * inside the published JavaScript, 6.69 kB gzip, and tripped the size gate on a
 * change that added no runtime code at all. This file is not published, so the
 * argument costs nothing here; each suppression keeps a one-line directive that
 * points back, so adding a new one is still a deliberate act.
 *
 * `react-hooks/set-state-in-effect` — the effects it reports synchronise with
 * something outside React: a fetch, the session cookie, or a browser capability
 * that does not exist during SSR. Marking `loading` before the request and
 * writing the result after it is the shape this SDK exposes, and the alternative
 * is a data-fetching dependency the package deliberately does not have — it is
 * zero-config for consumers by design. A cascading render on a resolved fetch is
 * the cost that buys the seamless API.
 *
 * `react-hooks/refs` — two shapes, both deliberate. A ref read during render,
 * where rebuilding the value when the caller's object literal changes identity
 * would discard accumulated state; and a ref mirror written during render,
 * because React queues state updates rather than applying them, so a callback
 * created this render would otherwise close over the previous value.
 *
 * Both rules report what the React Compiler would decline, and no package here
 * runs it. Turn them back on in the change that adds the compiler, and read the
 * suppressions first.
 *
 * `react-refresh/only-export-components` in `ui/TuringSearchField.tsx` — a
 * compound component. `TuringSearchField.Input` and its siblings are attached
 * with `Object.assign`, so the export is a const rather than a function
 * declaration and Fast Refresh cannot see a component in it. Splitting would put
 * the root and its parts in different modules, which is the one thing a compound
 * component must not be.
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
    rules: {
      'react-hooks/purity': 'off',
      // T1027 — and the same for the two rules that speak for the compiler. A
      // story mounts a scripted conversation and steps it forward on a timer:
      // `setState` in an effect is the demonstration, not a defect in it.
      'react-hooks/set-state-in-effect': 'off',
      'react-hooks/refs': 'off',
    },
  },
]
