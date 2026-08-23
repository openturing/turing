// T1000 — shim. The provider and the hook live in the design system; this module
// keeps its path so the pages reading the current user need no edit.
//
// A pure re-export rather than a wrapper: the package's provider takes the fetch
// as a prop, and which endpoint holds this product's user is the product's to
// say, so the shell passes it at the mount point — there is exactly one, in
// `app/bento/bento.root.page.tsx`.
//
// Two differences worth knowing at the call sites. The user is now `VigUser`,
// which is `TurUser` without `password`, `realm` and `turGroups`; nothing read
// those off the context — the profile page fetches its own `TurUser` through the
// service and takes only `refreshUser` from here — and `TurUser` still exists in
// `models/auth/user.ts` for the service payloads that do use them. And the
// context now reports `status`, which is the fix the package made after the copy
// diverged: `user` alone could not distinguish a request in flight from an
// expired session from an account with no name, so every consumer invented its
// own guess and `BentoUserMenu`'s guess left a skeleton on screen for ever when
// the fetch failed.
export { UserProvider, useCurrentUser } from "@viglet/viglet-design-system";
export type { VigUser } from "@viglet/viglet-design-system";
