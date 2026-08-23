// T1003 — shim. BentoUserMenu lives in the design system; this module keeps its
// path so the shell needs no edit beyond the props.
//
// T992 could not move it: the package's copy reads the user from the design
// system's own UserContext, and this product still mounted the console-era
// provider. T1000 made that provider a re-export of the package's, so the menu
// now reads the very context the shell mounts, and the two-providers-one-user
// problem is gone rather than worked around.
//
// Its routes and the entries they gate are props, which is where they belong:
// whether this product has tenancy, and whether this reader may administer it,
// are questions only the product can answer — it answers them by passing a route
// or omitting one, so the package never mirrors a feature model it cannot see.
export { BentoUserMenu, type BentoUserMenuProps } from "@viglet/viglet-design-system/bento";
