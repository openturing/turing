import { jsx as _jsx } from "react/jsx-runtime";
/**
 * Headless "assistant is thinking" typing indicator — a row of dots with a
 * staggered {@code animation-delay} on each, the shape both viglet.com and the
 * admin reached for while a reply streams. The bounce/pulse keyframe itself is
 * the app's to define (pass its class via {@code classNames.dot}); the component
 * only lays out the dots and staggers them.
 *
 * <p><b>Design-agnostic by construction:</b> it renders structure only — no
 * colors, sizes, or the animation itself, just the per-dot delay that turns the
 * app's keyframe into a wave. Skin via {@code className}/{@code classNames},
 * localize the screen-reader text via {@code label}. This is why the admin
 * console and viglet.com can share one implementation while keeping their looks.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringThinkingDots({ count = 3, delayStep = 0.18, label = "Thinking…", className, classNames, }) {
    return (_jsx("span", { className: className !== null && className !== void 0 ? className : classNames === null || classNames === void 0 ? void 0 : classNames.root, role: "status", "aria-label": label, "data-turing-thinking-dots": "", children: Array.from({ length: count }, (_, i) => (_jsx("span", { "aria-hidden": "true", className: classNames === null || classNames === void 0 ? void 0 : classNames.dot, style: { animationDelay: `${i * delayStep}s` } }, i))) }));
}
//# sourceMappingURL=TuringThinkingDots.js.map