import { type ReactNode } from "react";
/**
 * Compiles D2 source into an SVG string. Override
 * {@link TuringD2DiagramProps.render} with one of these to use a server route,
 * a shared engine instance, or a different diagram library.
 */
export type TuringD2Renderer = (code: string) => Promise<string>;
/** Per-slot class names — the component ships ZERO visual styling of its own. */
export interface TuringD2DiagramClassNames {
    /** Wrapper around the whole widget (loading / ready / error states). */
    root?: string;
    /** Wrapper around the rendered SVG. */
    diagram?: string;
    /** The loading placeholder. */
    loading?: string;
    /** The error caption shown above the source fallback. */
    error?: string;
    /** The `<pre>` source fallback (engine missing or compile failed). */
    code?: string;
}
/** Human-readable strings — pass localized values from the host app. */
export interface TuringD2DiagramLabels {
    /** Loading placeholder text. Default `"Rendering diagram…"`. */
    loading?: string;
    /** Caption shown above the source when rendering fails. Default `"Could not render diagram"`. */
    error?: string;
}
export interface TuringD2DiagramProps {
    /** The raw ```d2 diagram source. */
    code: string;
    /**
     * Override the compile→SVG engine. Defaults to a LAZY dynamic import of the
     * optional {@code @terrastruct/d2} peer — the literal {@code import()} lets the
     * host bundler split it into its own chunk and tree-shake it out of apps that
     * never render a diagram. When the peer is not installed the engine load
     * rejects and the component falls back to the source (see {@link code}).
     */
    render?: TuringD2Renderer;
    /** Sketch (hand-drawn) mode for the default engine. Default `false`. */
    sketch?: boolean;
    /** Replaces the loading placeholder. Defaults to the {@code loading} label. */
    loadingFallback?: ReactNode;
    /** Convenience alias for {@link TuringD2DiagramClassNames.root}. */
    className?: string;
    classNames?: TuringD2DiagramClassNames;
    labels?: TuringD2DiagramLabels;
}
/**
 * Headless renderer for an assistant ```d2 block. Compiles the D2 source to an
 * SVG via a lazily-loaded engine and renders it; while compiling it shows a
 * placeholder, and if the engine is unavailable (the optional
 * {@code @terrastruct/d2} peer is not installed) or the source fails to compile
 * it falls back to the raw source so nothing is lost.
 *
 * <p>This is the renderer {@code TuringRichContent} dispatches ```d2 segments to
 * (pass it as the {@code d2} prop). {@code render.md} already instructs agents
 * to emit ```d2 blocks; this is the piece that draws them.</p>
 *
 * <p><b>Design-agnostic by construction:</b> it renders structure only and
 * carries no styling, markdown, or icon dependency of its own, and the D2 engine
 * is an OPTIONAL peer reached through a lazy {@code import()} — so the admin
 * console and viglet.com share one implementation while keeping their looks, and
 * apps that never show a diagram never pay for the engine. Skin via
 * {@code className}/{@code classNames}, localize via {@code labels}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export declare function TuringD2Diagram({ code, render, sketch, loadingFallback, className, classNames, labels, }: Readonly<TuringD2DiagramProps>): import("react").JSX.Element;
//# sourceMappingURL=TuringD2Diagram.d.ts.map