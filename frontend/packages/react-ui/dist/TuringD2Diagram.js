import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from "react";
// One engine instance shared across diagrams — the WASM module is expensive to
// boot and the dynamic import resolves to the same chunk every time.
let d2EnginePromise = null;
function loadD2Engine() {
    if (!d2EnginePromise) {
        // Literal specifier → the host bundler emits a separate chunk, loaded only
        // when a diagram actually renders. Tree-shaken away when this component is
        // never used. A missing optional peer rejects here → source fallback.
        d2EnginePromise = import("@terrastruct/d2").then((mod) => new mod.D2());
    }
    return d2EnginePromise;
}
async function defaultRender(code, sketch) {
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
export function TuringD2Diagram({ code, render, sketch = false, loadingFallback, className, classNames, labels, }) {
    var _a;
    const [svg, setSvg] = useState(null);
    const [status, setStatus] = useState("loading");
    useEffect(() => {
        let cancelled = false;
        setStatus("loading");
        setSvg(null);
        const renderer = render !== null && render !== void 0 ? render : ((c) => defaultRender(c, sketch));
        renderer(code)
            .then((out) => {
            if (cancelled)
                return;
            setSvg(out);
            setStatus("ready");
        })
            .catch(() => {
            if (!cancelled)
                setStatus("error");
        });
        return () => {
            cancelled = true;
        };
    }, [code, render, sketch]);
    const wrapperClass = className !== null && className !== void 0 ? className : classNames === null || classNames === void 0 ? void 0 : classNames.root;
    if (status === "error") {
        // Lossless fallback: show the diagram source so it is still readable when
        // the engine is missing (optional peer not installed) or the source is
        // invalid D2. Mirrors TuringRichContent's "render the fence" degradation.
        return (_jsxs("div", { className: wrapperClass, "data-turing-d2": "error", children: [(labels === null || labels === void 0 ? void 0 : labels.error) && (_jsx("span", { className: classNames === null || classNames === void 0 ? void 0 : classNames.error, children: labels.error })), _jsx("pre", { className: classNames === null || classNames === void 0 ? void 0 : classNames.code, children: _jsx("code", { children: code }) })] }));
    }
    if (status === "loading") {
        return (_jsx("div", { className: wrapperClass, "data-turing-d2": "loading", children: loadingFallback !== null && loadingFallback !== void 0 ? loadingFallback : (_jsx("span", { className: classNames === null || classNames === void 0 ? void 0 : classNames.loading, children: (_a = labels === null || labels === void 0 ? void 0 : labels.loading) !== null && _a !== void 0 ? _a : "Rendering diagram…" })) }));
    }
    return (_jsx("div", { className: wrapperClass, "data-turing-d2": "ready", children: _jsx("div", { className: classNames === null || classNames === void 0 ? void 0 : classNames.diagram, dangerouslySetInnerHTML: { __html: svg !== null && svg !== void 0 ? svg : "" } }) }));
}
//# sourceMappingURL=TuringD2Diagram.js.map