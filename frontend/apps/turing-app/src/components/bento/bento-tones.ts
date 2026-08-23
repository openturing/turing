// T1002 — half a shim, which is the honest shape of this file.
//
// `BentoTone` is the package's: VDS9 made a tone a token, so the vocabulary of
// tones is shared and re-exported here. `BENTO_TONE_GRADIENTS` is not, because
// the package deliberately does not ship a Tailwind gradient map — a shared
// component that names its own colours makes one of two products look wrong,
// and the package's answer is `bentoChipClass(tone)` over CSS custom properties.
//
// Two heroes here still index this map directly. Replacing them with the token
// path is T996, which owes a proof that the swap is invisible in this product;
// until then the map is declared here and nowhere else.
export { type BentoTone } from "@viglet/viglet-design-system/bento";

import type { BentoTone } from "@viglet/viglet-design-system/bento";

/**
 * Tailwind gradients per tone, applied to a tile's icon chip — the surface
 * itself stays neutral (frosted glass) so colours do not visually compete.
 */
export const BENTO_TONE_GRADIENTS: Record<BentoTone, string> = {
  blue:    "from-blue-600 to-indigo-600",
  indigo:  "from-indigo-600 to-fuchsia-600",
  violet:  "from-violet-600 to-purple-600",
  emerald: "from-emerald-600 to-teal-600",
  amber:   "from-amber-500 to-orange-500",
  rose:    "from-rose-600 to-pink-600",
  slate:   "from-slate-600 to-slate-700",
};
