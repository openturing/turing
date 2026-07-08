// Block AG (T570) — the legacy console was retired; only two route groups
// survive outside the bento tree: the bento tree itself and the standalone,
// chrome-free voice kiosk. All the old per-surface `*.routes.tsx` files were
// deleted with the console pages they mounted.
export { BentoRoutes } from "./bento.routes";
export { VoiceKioskRoutes } from "./voice-kiosk.routes";
