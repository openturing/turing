// ESM shim — React 19 exports useSyncExternalStore natively,
// so the CJS shim (which does require('react')) is unnecessary
// and breaks Rolldown's browser CJS interop.
export { useSyncExternalStore } from "react";
