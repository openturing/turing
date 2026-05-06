/**
 * Color tones for Bento tiles. Each tone maps to a Tailwind gradient
 * applied to the icon chip — keeping the surface itself neutral
 * (frosted-glass) so colors don't visually compete.
 */
export type BentoTone =
  | "blue"
  | "indigo"
  | "violet"
  | "emerald"
  | "amber"
  | "rose"
  | "slate";

export const BENTO_TONE_GRADIENTS: Record<BentoTone, string> = {
  blue:    "from-blue-600 to-indigo-600",
  indigo:  "from-indigo-600 to-fuchsia-600",
  violet:  "from-violet-600 to-purple-600",
  emerald: "from-emerald-600 to-teal-600",
  amber:   "from-amber-500 to-orange-500",
  rose:    "from-rose-600 to-pink-600",
  slate:   "from-slate-600 to-slate-700",
};
