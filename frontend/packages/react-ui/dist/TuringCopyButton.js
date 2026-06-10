import { jsx as _jsx } from "react/jsx-runtime";
import { useEffect, useRef, useState } from "react";
/**
 * Headless copy-to-clipboard button with a self-resetting "copied" confirmation.
 * Centralises the {@code navigator.clipboard.writeText} + transient-state idiom
 * that the admin chat list and other surfaces each re-implemented by hand.
 *
 * <p><b>Design-agnostic by construction:</b> it renders a single {@code <button>}
 * and carries no styling of its own. Skin the two states via
 * {@code className}/{@code classNames} ({@code button} is always applied,
 * {@code copied}/{@code idle} toggle with the confirmation), localize via
 * {@code labels}, and supply state {@code icons} from whatever icon set the app
 * uses. This is why the admin console and viglet.com can share one
 * implementation while keeping their distinct looks.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringCopyButton({ value, resetMs = 2000, onCopy, className, classNames, labels, icons, }) {
    var _a, _b, _c, _d;
    const [copied, setCopied] = useState(false);
    const timerRef = useRef(null);
    // Clear any pending reset on unmount so we never call setState on a gone node.
    useEffect(() => () => {
        if (timerRef.current)
            clearTimeout(timerRef.current);
    }, []);
    const copyLabel = (_a = labels === null || labels === void 0 ? void 0 : labels.copy) !== null && _a !== void 0 ? _a : "Copy";
    const copiedLabel = (_b = labels === null || labels === void 0 ? void 0 : labels.copied) !== null && _b !== void 0 ? _b : "Copied";
    const activeLabel = copied ? copiedLabel : copyLabel;
    const handleClick = () => {
        // Guard for non-browser / insecure-context environments where the
        // Clipboard API is unavailable; do nothing rather than throw.
        if (typeof navigator === "undefined" || !navigator.clipboard)
            return;
        void navigator.clipboard.writeText(value).then(() => {
            onCopy === null || onCopy === void 0 ? void 0 : onCopy(value);
            setCopied(true);
            if (timerRef.current)
                clearTimeout(timerRef.current);
            timerRef.current = setTimeout(() => setCopied(false), resetMs);
        });
    };
    const buttonClass = [
        className !== null && className !== void 0 ? className : classNames === null || classNames === void 0 ? void 0 : classNames.button,
        copied ? classNames === null || classNames === void 0 ? void 0 : classNames.copied : classNames === null || classNames === void 0 ? void 0 : classNames.idle,
    ]
        .filter(Boolean)
        .join(" ");
    return (_jsx("button", { type: "button", className: buttonClass || undefined, onClick: handleClick, "aria-label": activeLabel, title: activeLabel, "data-turing-copy-button": copied ? "copied" : "idle", children: copied ? ((_c = icons === null || icons === void 0 ? void 0 : icons.copied) !== null && _c !== void 0 ? _c : copiedLabel) : ((_d = icons === null || icons === void 0 ? void 0 : icons.copy) !== null && _d !== void 0 ? _d : copyLabel) }));
}
//# sourceMappingURL=TuringCopyButton.js.map