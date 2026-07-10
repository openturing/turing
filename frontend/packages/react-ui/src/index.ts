/**
 * `@viglet/turing-react-ui` — headless React UI primitives for Viglet Turing ES.
 *
 * Design-agnostic chat / rich-content components shared across the admin
 * console and viglet.com. The package carries NO API or axios dependency (that
 * lives in `@viglet/turing-react-sdk`) and NO baked-in styling — every
 * component is skinned by the host app via `classNames` / `labels` / `icons`,
 * so each surface keeps its own look while sharing one implementation. As an
 * alternative to per-slot `classNames`, apps can import the optional token
 * stylesheet (`@viglet/turing-react-ui/styles.css`) and theme via `--turing-ui-*`
 * CSS custom properties on a `.turing-ui-theme` wrapper (T305).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export { TuringHtmlSandbox } from "./TuringHtmlSandbox";
export type {
  TuringHtmlSandboxProps,
  TuringHtmlSandboxClassNames,
  TuringHtmlSandboxLabels,
  TuringHtmlSandboxIcons,
} from "./TuringHtmlSandbox";

export { TuringRichContent, splitRichContent } from "./TuringRichContent";
export type {
  TuringRichContentProps,
  TuringRichContentClassNames,
  TuringRichSegment,
  TuringMarkdownSegmentProps,
  TuringD2Props,
  TuringHtmlSegmentProps,
} from "./TuringRichContent";

export { TuringMarkdown, resolveSandboxUrl } from "./TuringMarkdown";
export type { TuringMarkdownProps } from "./TuringMarkdown";

export { TuringD2Diagram } from "./TuringD2Diagram";
export type {
  TuringD2DiagramProps,
  TuringD2DiagramClassNames,
  TuringD2DiagramLabels,
  TuringD2Renderer,
} from "./TuringD2Diagram";

export { TuringCopyButton } from "./TuringCopyButton";
export type {
  TuringCopyButtonProps,
  TuringCopyButtonClassNames,
  TuringCopyButtonLabels,
  TuringCopyButtonIcons,
} from "./TuringCopyButton";

export { TuringThinkingDots } from "./TuringThinkingDots";
export type {
  TuringThinkingDotsProps,
  TuringThinkingDotsClassNames,
} from "./TuringThinkingDots";

export { TuringCodeBlock } from "./TuringCodeBlock";
export type {
  TuringCodeBlockProps,
  TuringCodeBlockClassNames,
  TuringCodeBlockLabels,
} from "./TuringCodeBlock";

export { TuringChatMessage } from "./TuringChatMessage";
export type {
  TuringChatMessageProps,
  TuringChatMessageRole,
  TuringChatMessageClassNames,
} from "./TuringChatMessage";

export { TuringSourceChips } from "./TuringSourceChips";
export type {
  TuringSourceChipsProps,
  TuringSourceChipsClassNames,
  TuringSourceChipsLabels,
  TuringSourceChipsIcons,
  TuringRagSource,
  TuringSourceConfidence,
} from "./TuringSourceChips";

export { TuringToolActivity } from "./TuringToolActivity";
export type {
  TuringToolActivityProps,
  TuringToolActivityClassNames,
  TuringToolActivityLabels,
  TuringToolActivityIcons,
  TuringToolCall,
} from "./TuringToolActivity";

export { TuringGenerativeContent } from "./TuringGenerativeContent";
export type {
  TuringGenerativeContentProps,
  TuringGenerativeContentClassNames,
  TuringGenerativeItem,
  TuringGenerativeComponentProps,
  TuringGenerativeRegistry,
} from "./TuringGenerativeContent";

// T636 / §XXVII.4 — persona-adaptive & content-validation primitives
// ("same question, different eyes" + "validate content as persona X").
export { TuringPersonaPicker } from "./TuringPersonaPicker";
export type {
  TuringPersonaPickerProps,
  TuringPersonaPickerClassNames,
  TuringPersonaPickerLabels,
  TuringPersonaPickerIcons,
  TuringPersonaOption,
} from "./TuringPersonaPicker";

export { TuringContentFit } from "./TuringContentFit";
export type {
  TuringContentFitProps,
  TuringContentFitClassNames,
  TuringContentFitLabels,
  TuringContentFitIcons,
  TuringContentFitResult,
  TuringContentFitMisfit,
} from "./TuringContentFit";

// T442 / §XXIII.1 — answer-as-an-app generative components (comparison table,
// spec card, configurator) keyed by the built-in client-tool names. Headless,
// zero-dep; register against `useGenerativeUI` + `TuringGenerativeContent`.
export {
  TuringComparisonTable,
  TuringSpecCard,
  TuringConfigurator,
  ANSWER_AS_APP_COMPONENTS,
  formatTypedValue,
} from "./TuringAnswerAsApp";
export type {
  TuringAppFieldType,
  TuringAppControlType,
  TuringAppColumn,
  TuringAppField,
  TuringAppAction,
  TuringAppControl,
  TuringComparisonTableProps,
  TuringComparisonTableClassNames,
  TuringSpecCardProps,
  TuringSpecCardClassNames,
  TuringConfiguratorProps,
  TuringConfiguratorClassNames,
} from "./TuringAnswerAsApp";

export { TuringCitedAnswer, segmentCitedAnswer } from "./TuringCitedAnswer";
export type {
  TuringCitedAnswerProps,
  TuringCitedAnswerClassNames,
  TuringCitation,
  TuringCitedMark,
} from "./TuringCitedAnswer";

// Design-less search primitives migrated from `@viglet/turing-react-sdk` (T306).
// Pure renderers (props + render-props, no hook/API coupling); their structural
// data types are redeclared locally to keep this package zero-runtime-dep.
export { TuringSearchBar } from "./TuringSearchBar";
export type {
  TuringSearchBarProps,
  TuringSearchBarInputProps,
} from "./TuringSearchBar";

export { TuringResultList } from "./TuringResultList";
export type {
  TuringResultListProps,
  TuringResolvedDocument,
  TuringRawDocument,
} from "./TuringResultList";

export { TuringPagination } from "./TuringPagination";
export type {
  TuringPaginationProps,
  TuringPaginationItemData,
} from "./TuringPagination";

// Headless view half of the legacy `TuringSearchField`, split out in T307. The
// orchestrating Root (autocomplete + history + url-search hooks) stays in
// `@viglet/turing-react-sdk` and feeds this view its state/callbacks via props;
// the baked Tailwind defaults were stripped here to honor the headless contract.
export { TuringSearchFieldView } from "./TuringSearchFieldView";
export type {
  TuringSearchFieldViewProps,
  TuringSearchFieldViewContextValue,
  TuringSearchFieldViewInputProps,
  TuringSearchFieldViewButtonProps,
  TuringSearchFieldViewDropdownProps,
} from "./TuringSearchFieldView";

// Headless view half of the legacy `TuringWorkspacePanel`, split out in T308.
// The SSE subscription (`useTuringWorkspace`) stays in `@viglet/turing-react-sdk`
// and forwards `artifacts` + `status` to this view; `formatBytes` ships here too.
export {
  TuringWorkspacePanelView,
  formatBytes,
} from "./TuringWorkspacePanelView";
export type {
  TuringWorkspacePanelViewProps,
  TuringWorkspaceArtifact,
  TuringWorkspaceStatus,
} from "./TuringWorkspacePanelView";

// Optional token theming (T305) — an alternative to `classNames`. Import the
// stylesheet (`@viglet/turing-react-ui/styles.css`) and drive the look via
// `--turing-ui-*` custom properties on a `.turing-ui-theme` wrapper.
export {
  TURING_UI_THEME_CLASS,
  TURING_UI_TOKENS,
} from "./tokens";
export type { TuringUiTheme, TuringUiTokenName } from "./tokens";
