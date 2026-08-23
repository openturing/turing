// T996 — shim, and now a whole one. The tone vocabulary and the class that
// carries it are the design system's; nothing here names a hue any more.
//
// T1002 left `BENTO_TONE_GRADIENTS` declared here, a Record<BentoTone, string>
// of Tailwind class pairs, because two heroes indexed it directly and replacing
// them was this task. They now call `bentoChipClass(tone)`, which resolves
// `--bento-tone-from/-to` from the preset — so a product re-keys a tone by
// redefining two custom properties instead of forking a component that named
// blue.
//
// The swap is invisible in this product, which is what T996 asked to be shown
// rather than assumed. All fourteen preset values are exactly the Tailwind
// colours the deleted map named, tone for tone: blue 600→indigo 600, indigo
// 600→fuchsia 600, violet 600→purple 600, emerald 600→teal 600, amber 500→orange
// 500, rose 600→pink 600, slate 600→700. `.bento-chip` also defaults its angle
// to `to bottom right`, which is the `bg-linear-to-br` the call sites dropped.
export {
  BENTO_TONES,
  BENTO_TONE_CLASS,
  bentoChipClass,
  type BentoTone,
} from "@viglet/viglet-design-system/bento";
