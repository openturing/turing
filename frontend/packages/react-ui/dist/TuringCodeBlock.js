import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { TuringCopyButton, } from "./TuringCopyButton";
/**
 * Headless fenced code block with a built-in copy affordance — a `<pre><code>`
 * plus an optional header carrying the language tag and a {@link TuringCopyButton}.
 * Centralises the "code + copy" pattern so chat surfaces stop hand-rolling it.
 *
 * <p>It does not highlight syntax itself — the host markdown pipeline
 * ({@code rehype-highlight} etc.) styles the {@code <code>} children, and the
 * {@code classNames.code} slot carries any {@code language-xxx} class a
 * highlighter expects.</p>
 *
 * <p><b>Design-agnostic by construction:</b> it renders structure only and
 * carries no styling or icon dependency of its own. Skin via
 * {@code className}/{@code classNames}, localize via {@code labels}, and supply
 * copy {@code copyIcons} from whatever icon set the app uses. This is why the
 * admin console and viglet.com can share one implementation while keeping their
 * distinct looks.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringCodeBlock({ code, language, showCopy = true, copyIcons, className, classNames, labels, }) {
    var _a;
    const showHeader = Boolean(language) || showCopy;
    return (_jsxs("div", { className: className !== null && className !== void 0 ? className : classNames === null || classNames === void 0 ? void 0 : classNames.root, "data-turing-code-block": "", "data-language": language || undefined, children: [showHeader && (_jsxs("div", { className: classNames === null || classNames === void 0 ? void 0 : classNames.header, children: [language ? (_jsx("span", { className: classNames === null || classNames === void 0 ? void 0 : classNames.language, children: language })) : (_jsx("span", {})), showCopy && (_jsx(TuringCopyButton, { value: code, classNames: classNames === null || classNames === void 0 ? void 0 : classNames.copyButton, labels: labels === null || labels === void 0 ? void 0 : labels.copy, icons: copyIcons }))] })), _jsx("pre", { className: classNames === null || classNames === void 0 ? void 0 : classNames.pre, children: _jsx("code", { className: (_a = classNames === null || classNames === void 0 ? void 0 : classNames.code) !== null && _a !== void 0 ? _a : (language ? `language-${language}` : undefined), children: code }) })] }));
}
//# sourceMappingURL=TuringCodeBlock.js.map