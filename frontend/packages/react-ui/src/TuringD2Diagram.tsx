import { useEffect, useState, type ReactNode } from "react";

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
  /**
   * Accessible name for the rendered diagram (the `role="img"` SVG container).
   * Default `"Diagram"`.
   */
  diagram?: string;
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

// One engine instance shared across diagrams — the WASM module is expensive to
// boot and the dynamic import resolves to the same chunk every time.
let d2EnginePromise: Promise<import("@terrastruct/d2").D2> | null = null;

function loadD2Engine() {
  if (!d2EnginePromise) {
    // Literal specifier → the host bundler emits a separate chunk, loaded only
    // when a diagram actually renders. Tree-shaken away when this component is
    // never used. A missing optional peer rejects here → source fallback.
    d2EnginePromise = import("@terrastruct/d2").then((mod) => new mod.D2());
  }
  return d2EnginePromise;
}

async function defaultRender(code: string, sketch: boolean): Promise<string> {
  const d2 = await loadD2Engine();
  const result = await d2.compile(code, { sketch });
  return d2.render(result.diagram, result.renderOptions);
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
export function TuringD2Diagram({
  code,
  render,
  sketch = false,
  loadingFallback,
  className,
  classNames,
  labels,
}: Readonly<TuringD2DiagramProps>) {
  const [svg, setSvg] = useState<string | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "error">(
    "loading",
  );

  useEffect(() => {
    let cancelled = false;
    setStatus("loading");
    setSvg(null);
    const renderer = render ?? ((c: string) => defaultRender(c, sketch));
    renderer(code)
      .then((out) => {
        if (cancelled) return;
        setSvg(out);
        setStatus("ready");
      })
      .catch(() => {
        if (!cancelled) setStatus("error");
      });
    return () => {
      cancelled = true;
    };
  }, [code, render, sketch]);

  const wrapperClass = className ?? classNames?.root;

  if (status === "error") {
    // Lossless fallback: show the diagram source so it is still readable when
    // the engine is missing (optional peer not installed) or the source is
    // invalid D2. Mirrors TuringRichContent's "render the fence" degradation.
    return (
      <div className={wrapperClass} data-turing-d2="error">
        {labels?.error && (
          <span className={classNames?.error} role="alert">
            {labels.error}
          </span>
        )}
        <pre className={classNames?.code}>
          <code>{code}</code>
        </pre>
      </div>
    );
  }

  if (status === "loading") {
    return (
      <div
        className={wrapperClass}
        data-turing-d2="loading"
        role="status"
        aria-live="polite"
      >
        {loadingFallback ?? (
          <span className={classNames?.loading}>
            {labels?.loading ?? "Rendering diagram…"}
          </span>
        )}
      </div>
    );
  }

  return (
    <div className={wrapperClass} data-turing-d2="ready">
      {/* D2-generated SVG, embedded as raw markup like every D2 web integration.
          The diagram source is assistant-generated; D2 escapes text labels and
          emits no <script>, so this is the conventional, safe embed for D2. */}
      <div
        className={classNames?.diagram}
        // role="img" is the conventional a11y wrapper for INLINE svg markup —
        // an <img> would need the SVG serialized to a data URL, losing the
        // crisp inline render. NOSONAR S6819
        role="img"
        aria-label={labels?.diagram ?? "Diagram"}
        dangerouslySetInnerHTML={{ __html: svg ?? "" }}
      />
    </div>
  );
}
