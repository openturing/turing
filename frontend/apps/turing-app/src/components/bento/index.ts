// Importing the stylesheet here means any consumer of a Bento component
// automatically pulls in the iOS-flavored animations and frosted-glass
// utilities — no separate CSS import to remember.
//
// T988 — the sheet now comes from the package. Its bento.css is a strict
// superset of the local bento.styles.css that stood here: every class, custom
// property and keyframe this product declared is in it, and none is missing.
// The local file is left in place for the deletions T989 and T990 own.
import "@viglet/viglet-design-system/bento.css";

// T988 — representative re-points, ahead of the wholesale ones. These four
// cover the risks the block is a bet on: the frosted surface and the hover
// lift on a tile, the section and hero that frame every bento page, and the
// fade custom property that drives the save-bar morph. The 118 pages import
// from this barrel and do not change.
export { BentoTile, type BentoTileProps } from "@viglet/viglet-design-system/bento";
export { BentoCountTile, type BentoCountTileProps } from "@viglet/viglet-design-system/bento";
export { BentoSection, type BentoSectionProps } from "@viglet/viglet-design-system/bento";
export {
  BentoHero,
  BentoBackLink,
  type BentoHeroProps,
} from "@viglet/viglet-design-system/bento";
export { BENTO_TONE_GRADIENTS, type BentoTone } from "./bento-tones";
export { BentoUserMenu } from "./bento-user-menu";
export { BentoBackToTop } from "./bento-back-to-top";
export { BentoEmptyState, type BentoEmptyStateProps } from "./bento-empty-state";
export { BentoActivationCard, type BentoActivationCardProps } from "./bento-activation-card";
export { BentoNoLlmState, type BentoNoLlmStateProps } from "./bento-no-llm-state";
export { BentoQuotaBanner, useQuotaRefusal } from "./bento-quota-banner";
export {
  BentoVigletActivationDialog,
  type BentoVigletActivationDialogProps,
} from "./bento-viglet-activation-dialog";
export { BentoShortcutsDialog, type BentoShortcutsDialogProps } from "./bento-shortcuts-dialog";
export {
  BentoFirstRunTour,
  type BentoFirstRunTourProps,
  BENTO_TOUR_SEEN_KEY,
  hasSeenBentoTour,
} from "./bento-first-run-tour";
export { BentoFormSection, type BentoFormSectionProps } from "./bento-form-section";
export { BentoSaveBar, type BentoSaveBarProps } from "./bento-save-bar";
export { BentoScrollSaveBar } from "./bento-scroll-save-bar";
export { BentoFormHero, type BentoFormHeroProps } from "./bento-form-hero";
export { useBentoScrollFade } from "@viglet/viglet-design-system/bento";
export { BentoStatusMarker, type BentoStatusMarkerProps } from "./bento-status-marker";
export { BentoHeroIconPicker, type BentoHeroIconPickerProps } from "./bento-hero-icon-picker";
export { BentoInlineEdit, type BentoInlineEditProps } from "./bento-inline-edit";
export {
  BentoEntityShell,
  type BentoEntityShellProps,
  type BentoEntityShellRenderArgs,
  type BentoIdentity,
  type BentoShellFormState,
} from "./bento-entity-shell";
export {
  BentoListPage,
  type BentoListPageProps,
  BentoTileGrid,
  type BentoTileGridProps,
  BentoEntityTile,
  type BentoEntityTileProps,
} from "./bento-list-page";
export {
  BENTO_EMPHASIS_SPAN,
  BENTO_EMPHASIS_NEXT,
  type BentoEmphasis,
  type BentoLayoutEntry,
  type BentoLayoutResponse,
  type BentoLayoutSource,
  type ResolvedBentoItem,
  resolveBentoLayout,
  toBentoLayoutEntries,
} from "./bento-layout";
export { BentoNavRail } from "./bento-nav-rail";
export { BentoCommandPalette, type BentoCommandPaletteProps } from "./bento-command-palette";
export {
  BENTO_NAV_ITEMS,
  BENTO_NAV_SECTIONS,
  BENTO_SPAN_FEATURED,
  BENTO_SPAN_WIDE,
  BENTO_SPAN_SQUARE,
  bentoNavTarget,
  bentoSectionByAreaRoute,
  bentoSectionAreaRoute,
  useVisibleBentoNav,
  useVisibleBentoSections,
  type BentoNavGroup,
  type BentoNavItem,
  type BentoNavSection,
  type BentoNavSectionId,
} from "./bento-nav.config";
export {
  BentoActionsMenu,
  type BentoActionsMenuItem,
  type BentoActionsMenuProps,
  type BentoActionTone,
} from "./bento-actions-menu";
