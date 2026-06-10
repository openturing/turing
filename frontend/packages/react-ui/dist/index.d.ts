/**
 * `@viglet/turing-react-ui` — headless React UI primitives for Viglet Turing ES.
 *
 * Design-agnostic chat / rich-content components shared across the admin
 * console and viglet.com. The package carries NO API or axios dependency (that
 * lives in `@viglet/turing-react-sdk`) and NO baked-in styling — every
 * component is skinned by the host app via `classNames` / `labels` / `icons`,
 * so each surface keeps its own look while sharing one implementation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export { TuringHtmlSandbox } from "./TuringHtmlSandbox";
export type { TuringHtmlSandboxProps, TuringHtmlSandboxClassNames, TuringHtmlSandboxLabels, TuringHtmlSandboxIcons, } from "./TuringHtmlSandbox";
export { TuringRichContent, splitRichContent } from "./TuringRichContent";
export type { TuringRichContentProps, TuringRichContentClassNames, TuringRichSegment, TuringMarkdownSegmentProps, TuringD2Props, TuringHtmlSegmentProps, } from "./TuringRichContent";
export { TuringMarkdown, resolveSandboxUrl } from "./TuringMarkdown";
export type { TuringMarkdownProps } from "./TuringMarkdown";
export { TuringD2Diagram } from "./TuringD2Diagram";
export type { TuringD2DiagramProps, TuringD2DiagramClassNames, TuringD2DiagramLabels, TuringD2Renderer, } from "./TuringD2Diagram";
export { TuringCopyButton } from "./TuringCopyButton";
export type { TuringCopyButtonProps, TuringCopyButtonClassNames, TuringCopyButtonLabels, TuringCopyButtonIcons, } from "./TuringCopyButton";
export { TuringThinkingDots } from "./TuringThinkingDots";
export type { TuringThinkingDotsProps, TuringThinkingDotsClassNames, } from "./TuringThinkingDots";
export { TuringCodeBlock } from "./TuringCodeBlock";
export type { TuringCodeBlockProps, TuringCodeBlockClassNames, TuringCodeBlockLabels, } from "./TuringCodeBlock";
//# sourceMappingURL=index.d.ts.map