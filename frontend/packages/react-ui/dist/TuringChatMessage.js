import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * Headless chat-message shell — the slot-driven bubble (avatar · author ·
 * status · attachments · content · actions · footer) that every Turing chat
 * surface rebuilds by hand. It only lays the slots out and tags the row with a
 * {@code data-role} attribute; what each slot contains is entirely the host's.
 *
 * <p>Slots are render-prop style {@link ReactNode}s, not configuration: pass an
 * {@code avatar} element, an {@code actions} cluster ({@link
 * import("./TuringCopyButton").TuringCopyButton}, retry, …), a {@code footer} of
 * suggestion chips, etc. Any slot left {@code undefined} renders nothing (and
 * skips its wrapper), so a bare user message collapses to just its content.</p>
 *
 * <p><b>Scope (deliberate):</b> this is the per-message bubble only. The
 * fully-branded app shell — page layout, the input bar, the suggestion-chip
 * styling — stays per-app on purpose; extracting it produces an abstraction
 * that fights both designs.</p>
 *
 * <p><b>Design-agnostic by construction:</b> it renders structure only — no
 * colors, spacing, or layout beyond the slot ordering. Skin every slot via
 * {@code className}/{@code classNames}; branch role-specific classes in the host
 * (it owns {@code role}), or target the {@code data-role} attribute in CSS. This
 * is why the admin console and viglet.com can share one implementation while
 * keeping their distinct looks.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringChatMessage({ role, children, avatar, name, status, attachments, actions, footer, className, classNames, }) {
    const showHeader = name != null || status != null;
    return (_jsxs("div", { className: className !== null && className !== void 0 ? className : classNames === null || classNames === void 0 ? void 0 : classNames.root, "data-turing-chat-message": "", "data-role": role, children: [avatar != null && _jsx("div", { className: classNames === null || classNames === void 0 ? void 0 : classNames.avatar, children: avatar }), _jsxs("div", { className: classNames === null || classNames === void 0 ? void 0 : classNames.body, children: [showHeader && (_jsxs("div", { className: classNames === null || classNames === void 0 ? void 0 : classNames.header, children: [name != null && _jsx("span", { className: classNames === null || classNames === void 0 ? void 0 : classNames.name, children: name }), status != null && (_jsx("span", { className: classNames === null || classNames === void 0 ? void 0 : classNames.status, children: status }))] })), attachments != null && (_jsx("div", { className: classNames === null || classNames === void 0 ? void 0 : classNames.attachments, children: attachments })), _jsx("div", { className: classNames === null || classNames === void 0 ? void 0 : classNames.content, children: children }), actions != null && (_jsx("div", { className: classNames === null || classNames === void 0 ? void 0 : classNames.actions, children: actions })), footer != null && (_jsx("div", { className: classNames === null || classNames === void 0 ? void 0 : classNames.footer, children: footer }))] })] }));
}
//# sourceMappingURL=TuringChatMessage.js.map