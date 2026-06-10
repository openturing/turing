import { jsx as _jsx, Fragment as _Fragment } from "react/jsx-runtime";
import { TuringHtmlSandbox, } from "./TuringHtmlSandbox";
// Fenced ```html / ```d2 block — see the SDK's rich-content.ts for the canonical
// version. Trailing newline before the closing fence is trimmed from the body.
const SPECIAL_BLOCK = /```(html|d2)\r?\n([\s\S]*?)```/g;
/**
 * Splits {@code content} into ordered markdown / html / d2 segments. A reply
 * with no special block yields a single {@code markdown} segment, so callers
 * can always map over the result uniformly. This is the built-in default for
 * {@link TuringRichContent}; pass {@link TuringRichContentProps.split} to reuse
 * the SDK's {@code splitRichContent} instead (single source of truth).
 */
export function splitRichContent(content) {
    var _a;
    if (!content)
        return [];
    const segments = [];
    let lastIndex = 0;
    for (const match of content.matchAll(SPECIAL_BLOCK)) {
        const index = (_a = match.index) !== null && _a !== void 0 ? _a : 0;
        const before = content.slice(lastIndex, index);
        if (before)
            segments.push({ type: "markdown", text: before });
        const lang = match[1];
        segments.push({ type: lang, code: match[2].replace(/\r?\n$/, "") });
        lastIndex = index + match[0].length;
    }
    const after = content.slice(lastIndex);
    if (after)
        segments.push({ type: "markdown", text: after });
    return segments;
}
/**
 * Headless renderer for a rich assistant reply: splits the text into ordered
 * markdown / ```html / ```d2 segments and dispatches each to the right widget —
 * prose to the injected {@link TuringRichContentProps.markdown} component,
 * ```html to a sandboxed {@link TuringHtmlSandbox} (or a {@link html} override),
 * ```d2 to the optional {@link d2} renderer.
 *
 * <p>Replaces the per-app split-and-map duplication (the admin console's
 * {@code splitContent} + {@code AssistantContent}, and viglet.com's inline
 * {@code splitRichContent().map(...)}). Both apps now share this dispatcher
 * while keeping their own markdown setup, html skin, and look.</p>
 *
 * <p><b>Design-agnostic by construction:</b> it renders structure only and
 * carries no styling or markdown/icon dependency of its own. Skin via
 * {@code className}/{@code classNames}, plug markdown/html/d2 renderers in.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringRichContent({ content, markdown: Markdown, html: Html, htmlSandbox, d2: D2, split = splitRichContent, className, classNames, }) {
    const segments = split(content);
    const nodes = segments.map((seg, i) => {
        if (seg.type === "html") {
            return Html ? (_jsx(Html, { code: seg.code }, i)) : (_jsx(TuringHtmlSandbox, { code: seg.code, ...htmlSandbox }, i));
        }
        if (seg.type === "d2") {
            // No d2 renderer yet → render the block losslessly as a markdown code
            // fence, so the diagram source is still shown until T297 lands.
            return D2 ? (_jsx(D2, { code: seg.code }, i)) : (_jsx(Markdown, { text: `\`\`\`d2\n${seg.code}\n\`\`\`` }, i));
        }
        return _jsx(Markdown, { text: seg.text }, i);
    });
    const wrapperClass = className !== null && className !== void 0 ? className : classNames === null || classNames === void 0 ? void 0 : classNames.root;
    if (!wrapperClass)
        return _jsx(_Fragment, { children: nodes });
    return _jsx("div", { className: wrapperClass, children: nodes });
}
//# sourceMappingURL=TuringRichContent.js.map