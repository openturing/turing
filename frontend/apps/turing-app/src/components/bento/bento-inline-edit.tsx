import { Input } from "@/components/ui/input";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

export interface BentoInlineEditProps {
  /** Current persisted value. */
  value: string;
  /**
   * Called when the user commits a non-empty change. Awaited if a Promise
   * is returned — used to keep the field in "saving" state while the
   * underlying mutation resolves.
   */
  onSave: (next: string) => void | Promise<void>;
  /** Multiline mode renders a textarea + Cmd/Ctrl+Enter to save (Enter just adds a line). */
  multiline?: boolean;
  /** Shown when value is empty and the field is not being edited. */
  placeholder?: string;
  /** Class applied to BOTH the display element and the input/textarea — keeps typography consistent so there's no visual jump entering/leaving edit mode. */
  className?: string;
  /** Accessible label for the input. */
  ariaLabel?: string;
  /**
   * Start in edit mode on mount — useful for "new entity" flows where
   * the placeholder field needs to be filled before anything else
   * makes sense. The user sees a focused input on landing.
   */
  autoFocus?: boolean;
  /**
   * Render as plain, non-editable text — no click-to-edit affordance.
   * Used by read-only surfaces (e.g. the shared GLOBAL BYO-infra pool
   * when the caller is not a platform admin).
   */
  readOnly?: boolean;
  /**
   * Push every keystroke up through `onSave` (live), instead of only on
   * blur/Enter. Used by "new entity" flows where the field stages locally
   * (no mutation per keystroke) and downstream gates — e.g. the Save
   * button's `titleMissing` check — must react as the user types. With
   * commit-on-blur, a user who types the name then clicks the (still
   * disabled) Save button never commits, so Save appears stuck disabled.
   */
  commitOnChange?: boolean;
}

/**
 * Click-to-edit inline text. Reads as plain text until clicked, then
 * swaps to a controlled input/textarea that commits on blur / Enter.
 *
 * Display mode shows a subtle bottom border on hover (the canonical
 * "this is editable" hint). Edit mode keeps the same font size /
 * weight as the display so the text doesn't jump when toggling.
 */
export function BentoInlineEdit({
  value,
  onSave,
  multiline = false,
  placeholder,
  className = "",
  ariaLabel,
  autoFocus = false,
  readOnly = false,
  commitOnChange = false,
}: Readonly<BentoInlineEditProps>) {
  const { t } = useTranslation();
  const [editing, setEditing] = useState(autoFocus);
  const [draft, setDraft] = useState(value);
  const [saving, setSaving] = useState(false);
  const inputRef = useRef<HTMLInputElement | HTMLTextAreaElement>(null);

  // External value changes (e.g. mutation finished) should reset our draft
  // when we're not actively editing — otherwise we'd overwrite the user's
  // in-progress text.
  useEffect(() => {
    if (!editing) setDraft(value);
  }, [value, editing]);

  useEffect(() => {
    if (editing && inputRef.current) {
      inputRef.current.focus();
      inputRef.current.select();
    }
  }, [editing]);

  async function commit() {
    const next = draft.trim();
    if (next === value) {
      setEditing(false);
      return;
    }
    if (!next && !placeholder) {
      // Refuse to save empty when there's no placeholder — keeps the
      // page from rendering a blank title. Bounce back to the original.
      setDraft(value);
      setEditing(false);
      return;
    }
    setSaving(true);
    try {
      await onSave(next);
    } finally {
      setSaving(false);
      setEditing(false);
    }
  }

  function cancel() {
    setDraft(value);
    setEditing(false);
  }

  // Live-commit path for "new entity" flows: keep local draft in sync AND
  // push the raw value up so downstream gates (e.g. the Save button) react
  // per keystroke. Trimming is left to the consumer's gate so the user can
  // still type spaces mid-word.
  function handleChange(next: string) {
    setDraft(next);
    if (commitOnChange) onSave(next);
  }

  const isEmpty = !value;

  // Read-only: plain text, no click target, no hover affordance. Kept
  // typographically identical to the editable display via `className`.
  if (readOnly) {
    return (
      <span className={`block w-full ${className}`} aria-label={ariaLabel}>
        {value || <span className="text-muted-foreground/80">{placeholder ?? "—"}</span>}
      </span>
    );
  }

  const sharedClass = `w-full bg-transparent outline-none border-b transition-colors duration-200 ${className}`;
  /*
   * Empty display state needs a permanent visual cue so the user
   * sees "click here to add a name". A dashed bottom border + a less
   * faded placeholder establishes an obvious affordance without
   * looking like a styled input. Filled state stays clean — the
   * subtle hover border is enough once there's actual text.
   */
  const displayClass = `${sharedClass} cursor-text rounded-sm ${
    isEmpty
      ? "border-dashed border-border/70 hover:border-foreground/40"
      : "border-transparent hover:border-border/60 focus-visible:border-border/80"
  } ${saving ? "opacity-60" : ""}`;
  const editClass = `${sharedClass} border-solid border-indigo-500/60 focus:border-indigo-500`;

  if (!editing) {
    return (
      <button
        type="button"
        onClick={() => setEditing(true)}
        className={`${displayClass} text-left`}
        aria-label={ariaLabel ?? t("forms.formActions.edit")}
        disabled={saving}
      >
        {value || (
          <span className="text-muted-foreground/80">{placeholder ?? "—"}</span>
        )}
      </button>
    );
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement | HTMLTextAreaElement>) {
    if (e.key === "Escape") {
      e.preventDefault();
      cancel();
      return;
    }
    if (e.key === "Enter") {
      // Single line: Enter commits.
      // Multiline: Cmd/Ctrl+Enter commits, plain Enter inserts a newline.
      if (!multiline || e.metaKey || e.ctrlKey) {
        e.preventDefault();
        commit();
      }
    }
  }

  if (multiline) {
    return (
      <textarea
        ref={inputRef as React.RefObject<HTMLTextAreaElement>}
        value={draft}
        onChange={(e) => handleChange(e.target.value)}
        onBlur={commit}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        aria-label={ariaLabel}
        rows={3}
        className={`${editClass} resize-none py-0.5 leading-snug`}
      />
    );
  }

  return (
    <Input
      ref={inputRef as React.RefObject<HTMLInputElement>}
      value={draft}
      onChange={(e) => handleChange(e.target.value)}
      onBlur={commit}
      onKeyDown={handleKeyDown}
      placeholder={placeholder}
      aria-label={ariaLabel}
      className={`${editClass} h-auto px-0 py-0 shadow-none focus-visible:ring-0`}
    />
  );
}
