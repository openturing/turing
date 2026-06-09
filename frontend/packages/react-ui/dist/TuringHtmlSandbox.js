import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useEffect, useState, } from "react";
import { createPortal } from "react-dom";
/**
 * Headless live-preview for an assistant ```html block — UI demos, animations,
 * and games (a `<canvas>` Pong). The fragment runs inside a sandboxed iframe
 * with `allow-scripts` (its JS executes) but WITHOUT `allow-same-origin`, so it
 * has no access to the host page. A fullscreen affordance (Esc to close) and an
 * optional code/preview toggle are built in.
 *
 * <p><b>Design-agnostic by construction:</b> the component renders structure
 * only — it carries no colors, borders, or spacing of its own (just the
 * iframe's functional layout). Skin it via {@link TuringHtmlSandboxProps.classNames}
 * / {@link TuringHtmlSandboxProps.className}, localize via {@code labels}, and
 * supply toolbar {@code icons} from whatever icon set the app uses. This is why
 * the Turing admin console and viglet.com can share one implementation while
 * keeping their distinct looks.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringHtmlSandbox({ code, height = 320, showCodeToggle = false, className, classNames, labels, icons, }) {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    const [fullscreen, setFullscreen] = useState(false);
    const [showCode, setShowCode] = useState(false);
    const title = (_a = labels === null || labels === void 0 ? void 0 : labels.title) !== null && _a !== void 0 ? _a : "Preview";
    const fullscreenLabel = (_b = labels === null || labels === void 0 ? void 0 : labels.fullscreen) !== null && _b !== void 0 ? _b : "Fullscreen";
    const closeLabel = (_c = labels === null || labels === void 0 ? void 0 : labels.close) !== null && _c !== void 0 ? _c : "Close";
    const codeLabel = (_d = labels === null || labels === void 0 ? void 0 : labels.showCode) !== null && _d !== void 0 ? _d : "Code";
    const previewLabel = (_e = labels === null || labels === void 0 ? void 0 : labels.showPreview) !== null && _e !== void 0 ? _e : "Preview";
    const iframeStyle = {
        width: "100%",
        height,
        border: 0,
        display: "block",
        background: "#fff",
    };
    return (_jsxs(_Fragment, { children: [_jsxs("div", { className: className !== null && className !== void 0 ? className : classNames === null || classNames === void 0 ? void 0 : classNames.root, "data-turing-html-sandbox": "", children: [_jsxs("div", { className: classNames === null || classNames === void 0 ? void 0 : classNames.header, children: [_jsx("span", { className: classNames === null || classNames === void 0 ? void 0 : classNames.label, children: title }), _jsxs("span", { className: classNames === null || classNames === void 0 ? void 0 : classNames.actions, children: [showCodeToggle && (_jsx("button", { type: "button", className: classNames === null || classNames === void 0 ? void 0 : classNames.button, onClick: () => setShowCode((v) => !v), children: showCode
                                            ? ((_f = icons === null || icons === void 0 ? void 0 : icons.preview) !== null && _f !== void 0 ? _f : previewLabel)
                                            : ((_g = icons === null || icons === void 0 ? void 0 : icons.code) !== null && _g !== void 0 ? _g : codeLabel) })), _jsx("button", { type: "button", className: classNames === null || classNames === void 0 ? void 0 : classNames.button, "aria-label": fullscreenLabel, title: fullscreenLabel, onClick: () => setFullscreen(true), children: (_h = icons === null || icons === void 0 ? void 0 : icons.fullscreen) !== null && _h !== void 0 ? _h : fullscreenLabel })] })] }), showCode ? (_jsx("pre", { className: classNames === null || classNames === void 0 ? void 0 : classNames.code, children: _jsx("code", { children: code }) })) : (_jsx("iframe", { srcDoc: buildSrcDoc(code), sandbox: "allow-scripts", className: classNames === null || classNames === void 0 ? void 0 : classNames.iframe, style: iframeStyle, title: title }))] }), fullscreen && (_jsx(TuringHtmlSandboxFullscreen, { code: code, title: title, closeLabel: closeLabel, closeIcon: icons === null || icons === void 0 ? void 0 : icons.close, classNames: classNames, onClose: () => setFullscreen(false) }))] }));
}
function TuringHtmlSandboxFullscreen({ code, title, closeLabel, closeIcon, classNames, onClose, }) {
    useEffect(() => {
        const onKey = (e) => {
            if (e.key === "Escape")
                onClose();
        };
        document.addEventListener("keydown", onKey);
        return () => document.removeEventListener("keydown", onKey);
    }, [onClose]);
    // SSR guard: the portal target only exists in the browser. Fullscreen is
    // only ever entered from a click, so this is browser-side in practice.
    if (typeof document === "undefined")
        return null;
    return createPortal(_jsxs("div", { className: classNames === null || classNames === void 0 ? void 0 : classNames.fullscreenRoot, children: [_jsxs("div", { className: classNames === null || classNames === void 0 ? void 0 : classNames.fullscreenHeader, children: [_jsx("span", { className: classNames === null || classNames === void 0 ? void 0 : classNames.fullscreenLabel, children: title }), _jsx("button", { type: "button", className: classNames === null || classNames === void 0 ? void 0 : classNames.fullscreenButton, "aria-label": closeLabel, title: closeLabel, onClick: onClose, children: closeIcon !== null && closeIcon !== void 0 ? closeIcon : closeLabel })] }), _jsx("iframe", { srcDoc: buildSrcDoc(code), sandbox: "allow-scripts", className: classNames === null || classNames === void 0 ? void 0 : classNames.fullscreenIframe, style: { flex: 1, width: "100%", border: 0, background: "#fff" }, title: `${title} (fullscreen)` })] }), document.body);
}
/** Wraps a fragment into a minimal self-contained document for the iframe. */
function buildSrcDoc(html) {
    return `<!DOCTYPE html><html><head><meta charset="utf-8"><style>html,body{margin:0;padding:8px;font-family:system-ui,sans-serif;box-sizing:border-box;}</style></head><body>${html}</body></html>`;
}
//# sourceMappingURL=TuringHtmlSandbox.js.map