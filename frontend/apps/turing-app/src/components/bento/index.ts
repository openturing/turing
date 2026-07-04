// Importing the stylesheet here means any consumer of a Bento component
// automatically pulls in the iOS-flavored animations and frosted-glass
// utilities — no separate CSS import to remember.
import "./bento.styles.css";

export { BentoTile, type BentoTileProps } from "./bento-tile";
export { BentoCountTile, type BentoCountTileProps } from "./bento-count-tile";
export { BentoSection, type BentoSectionProps } from "./bento-section";
export { BentoHero, type BentoHeroProps } from "./bento-hero";
export { BENTO_TONE_GRADIENTS, type BentoTone } from "./bento-tones";
export { BentoUserMenu } from "./bento-user-menu";
export { BentoBackToTop } from "./bento-back-to-top";
export { BentoFormSection, type BentoFormSectionProps } from "./bento-form-section";
export { BentoSaveBar, type BentoSaveBarProps } from "./bento-save-bar";
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
