import type { ReactNode } from "react";

/** Which side of the conversation this bubble belongs to. */
export type TuringChatMessageRole = "user" | "assistant";

/** Per-slot class names — the component ships ZERO visual styling of its own. */
export interface TuringChatMessageClassNames {
  /** The outermost row wrapping the avatar + body. */
  root?: string;
  /** Wrapper around the {@link TuringChatMessageProps.avatar} slot. */
  avatar?: string;
  /** The content column to the side of the avatar. */
  body?: string;
  /** The header row (author name + status). */
  header?: string;
  /** The author-name caption inside the header. */
  name?: string;
  /** Wrapper around the {@link TuringChatMessageProps.status} slot (e.g. a spinner). */
  status?: string;
  /** Wrapper around the {@link TuringChatMessageProps.attachments} slot. */
  attachments?: string;
  /** Wrapper around the message {@link TuringChatMessageProps.children content}. */
  content?: string;
  /** Wrapper around the {@link TuringChatMessageProps.actions} slot (copy, retry, …). */
  actions?: string;
  /** Wrapper around the {@link TuringChatMessageProps.footer} slot (suggestion chips, …). */
  footer?: string;
}

export interface TuringChatMessageProps {
  /**
   * Which side of the conversation this is. Drives the {@code data-role}
   * attribute (so CSS can target {@code [data-role="assistant"]}) and lets the
   * host pass role-specific {@link classNames}.
   */
  role: TuringChatMessageRole;
  /** The rendered message body — markdown, rich content, plain text, anything. */
  children?: ReactNode;
  /** Avatar node (image, gradient fallback, icon). Omit to render no avatar slot. */
  avatar?: ReactNode;
  /** Author label shown in the header (e.g. the assistant's name or "You"). */
  name?: ReactNode;
  /**
   * Inline status node placed next to the name — e.g. a "streaming" spinner.
   * Only rendered when provided.
   */
  status?: ReactNode;
  /** Attachment chips/preview row. Only rendered when provided. */
  attachments?: ReactNode;
  /**
   * Message actions (copy, retry, feedback…). Rendered after the content; the
   * host positions/reveals it (e.g. on hover) via {@link classNames}.
   */
  actions?: ReactNode;
  /** Follow-up affordances such as suggestion chips. Only rendered when provided. */
  footer?: ReactNode;
  /** Convenience alias for {@link TuringChatMessageClassNames.root}. */
  className?: string;
  classNames?: TuringChatMessageClassNames;
}

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
export function TuringChatMessage({
  role,
  children,
  avatar,
  name,
  status,
  attachments,
  actions,
  footer,
  className,
  classNames,
}: Readonly<TuringChatMessageProps>) {
  const showHeader = name != null || status != null;

  return (
    <div
      className={className ?? classNames?.root}
      data-turing-chat-message=""
      data-role={role}
    >
      {avatar != null && <div className={classNames?.avatar}>{avatar}</div>}
      <div className={classNames?.body}>
        {showHeader && (
          <div className={classNames?.header}>
            {name != null && <span className={classNames?.name}>{name}</span>}
            {status != null && (
              <span className={classNames?.status}>{status}</span>
            )}
          </div>
        )}
        {attachments != null && (
          <div className={classNames?.attachments}>{attachments}</div>
        )}
        <div className={classNames?.content}>{children}</div>
        {actions != null && (
          <div className={classNames?.actions}>{actions}</div>
        )}
        {footer != null && (
          <div className={classNames?.footer}>{footer}</div>
        )}
      </div>
    </div>
  );
}
