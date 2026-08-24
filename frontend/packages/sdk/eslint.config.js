import { library, browserAndNode } from '@viglet/eslint-config/library'

/**
 * T1025 — this package ships no React and no framework at all: it is the
 * zero-dependency client an Adobe EDS block or a plain `<script>` loads. It
 * also builds a node-side entry, so the globals are both.
 */
export default [
  ...library({ globals: browserAndNode }),
]
