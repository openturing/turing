import { IconCirclePlus, IconEye, IconInfoCircle, IconPencil } from "@tabler/icons-react";
import React, { useCallback, useMemo, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";
import { MetaPromptSheet, type MetaPromptTone } from "./meta-prompt-sheet";
import { Textarea } from "./textarea";

// ---------------------------------------------------------------------------
// Variable
// ---------------------------------------------------------------------------

export interface PromptVariable {
  /** The variable token, e.g. "{{question}}" */
  token: string;
  /** Short label shown in the badge, e.g. "question" */
  label: string;
}

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

/**
 * Optional "Help me write" affordance rendered inside the editor toolbar.
 * When provided, a button appears next to the Write/Preview toggle that
 * opens a {@link MetaPromptSheet} streaming directly into the editor's value.
 * Omit to render the editor without the AI helper.
 */
export interface PromptEditorMetaPrompt {
  /** Persisted user brief; null/empty starts the sheet blank. */
  brief?: string | null;
  /** When provided, the brief is saved each time Generate runs. */
  onBriefChange?: (value: string) => void;
  /** System message describing the field's purpose to the helper LLM. */
  fieldInstruction: string;
  /** Trigger button copy. */
  triggerLabel: string;
  /** Sheet header copy. */
  title: string;
  description: string;
  /** Brief textarea copy. */
  placeholder: string;
  hint?: string;
  /** Generate-button copy. */
  generateLabel: string;
  /** Pre-bundled color tone for the trigger and sheet accent. */
  tone?: MetaPromptTone;
  disabled?: boolean;
}

interface PromptEditorProps {
  /** Current prompt text (controlled) */
  value: string;
  /** Called when the prompt text changes */
  onChange: (value: string) => void;
  /** react-hook-form ref callback (optional) */
  fieldRef?: React.Ref<HTMLTextAreaElement>;
  /** Placeholder text */
  placeholder?: string;
  /** Number of visible rows */
  rows?: number;
  /** Variables that can be inserted into the prompt */
  variables?: PromptVariable[];
  /** Hint text shown in the info box */
  hint?: string;
  /** Whether variables are required (shows validation badges) */
  requireVariables?: boolean;
  /** When set, renders an inline "Help me write" button + MetaPromptSheet. */
  metaPrompt?: PromptEditorMetaPrompt;
}

// ---------------------------------------------------------------------------
// StatusBadge (internal)
// ---------------------------------------------------------------------------

function StatusBadge({ label, active }: Readonly<{ label: string; active: boolean }>) {
  return (
    <div className={`flex items-center gap-1.5 px-2 py-1 rounded-full border text-[10px] font-bold transition-all ${active
      ? "bg-emerald-500/10 text-emerald-600 border-emerald-500/20 dark:text-emerald-400 dark:border-emerald-400/20"
      : "bg-slate-100 text-slate-400 border-slate-200 opacity-60 dark:bg-slate-800 dark:border-slate-700"
      }`}>
      <div className={`w-1.5 h-1.5 rounded-full ${active ? "bg-emerald-500 animate-pulse" : "bg-slate-300 dark:bg-slate-600"}`} />
      {label.toUpperCase()}
    </div>
  );
}

// ---------------------------------------------------------------------------
// PromptEditor
// ---------------------------------------------------------------------------

export const PromptEditor: React.FC<PromptEditorProps> = ({
  value,
  onChange,
  fieldRef,
  placeholder = "You are a helpful assistant that...",
  rows = 8,
  variables,
  hint,
  requireVariables = false,
  metaPrompt,
}) => {
  const internalRef = useRef<HTMLTextAreaElement>(null);
  const [mode, setMode] = useState<"write" | "preview">("preview");

  const setRef = useCallback((el: HTMLTextAreaElement | null) => {
    (internalRef as React.MutableRefObject<HTMLTextAreaElement | null>).current = el;
    if (typeof fieldRef === "function") {
      fieldRef(el);
    } else if (fieldRef && typeof fieldRef === "object") {
      (fieldRef as React.MutableRefObject<HTMLTextAreaElement | null>).current = el;
    }
  }, [fieldRef]);

  const insertVariable = useCallback((token: string) => {
    const textarea = internalRef.current;
    if (textarea) {
      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const before = value.slice(0, start);
      const after = value.slice(end);
      onChange(before + token + after);
      setTimeout(() => {
        textarea.focus();
        textarea.setSelectionRange(start + token.length, start + token.length);
      }, 0);
    } else {
      onChange(value + token);
    }
  }, [value, onChange]);

  const variablePresence = useMemo(() => {
    if (!variables) return [];
    return variables.map((v) => ({
      ...v,
      present: value.includes(v.token),
    }));
  }, [variables, value]);

  const rowHeight = rows * 1.625;

  return (
    <div className="space-y-2">
      {/* Toolbar: variable buttons + write/preview toggle */}
      <div className="flex items-center justify-between gap-2">
        {variables && variables.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {variables.map((v) => (
              <button
                key={v.token}
                type="button"
                onClick={() => { setMode("write"); insertVariable(v.token); }}
                className="text-[10px] flex items-center gap-1 bg-amber-500/10 text-amber-600 border border-amber-500/20 px-2 py-1 rounded hover:bg-amber-500/20 transition-all font-mono dark:text-amber-400 dark:border-amber-400/20"
              >
                <IconCirclePlus className="w-3 h-3" /> {v.token}
              </button>
            ))}
          </div>
        ) : <div />}

        <div className="flex items-center gap-2 shrink-0">
          {metaPrompt && (
            <MetaPromptSheet
              value={value}
              onChange={onChange}
              metaPrompt={metaPrompt.brief ?? ""}
              onMetaPromptChange={metaPrompt.onBriefChange}
              fieldInstruction={metaPrompt.fieldInstruction}
              triggerVariant="button"
              tone={metaPrompt.tone ?? "violet"}
              triggerLabel={metaPrompt.triggerLabel}
              title={metaPrompt.title}
              description={metaPrompt.description}
              placeholder={metaPrompt.placeholder}
              hint={metaPrompt.hint}
              generateLabel={metaPrompt.generateLabel}
              disabled={metaPrompt.disabled}
            />
          )}

          <div className="flex items-center rounded-md border bg-muted/50 p-0.5 gap-0.5">
            <button
              type="button"
              onClick={() => setMode("preview")}
              className={`flex items-center gap-1 px-2.5 py-1 rounded text-xs font-medium transition-all ${mode === "preview"
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
                }`}
            >
              <IconEye className="size-3.5" />
              Preview
            </button>
            <button
              type="button"
              onClick={() => setMode("write")}
              className={`flex items-center gap-1 px-2.5 py-1 rounded text-xs font-medium transition-all ${mode === "write"
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
                }`}
            >
              <IconPencil className="size-3.5" />
              Write
            </button>
          </div>
        </div>
      </div>

      {/* Write mode: Textarea */}
      {mode === "write" && (
        <Textarea
          ref={setRef}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          rows={rows}
          placeholder={placeholder}
          className="font-mono text-sm leading-relaxed resize-y"
        />
      )}

      {/* Preview mode: Rendered Markdown */}
      {mode === "preview" && (
        <div
          className="rounded-md border bg-background px-3 py-2 overflow-y-auto prose prose-sm dark:prose-invert prose-neutral max-w-none break-words prose-p:my-2 prose-pre:my-2 prose-ul:my-2 prose-ol:my-2 prose-headings:my-3 prose-code:before:content-none prose-code:after:content-none prose-code:bg-muted prose-code:px-1 prose-code:py-0.5 prose-code:rounded prose-code:text-sm prose-pre:bg-muted prose-pre:border prose-pre:rounded-lg"
          style={{ minHeight: `${rowHeight}rem` }}
        >
          {value.trim() ? (
            <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
              {value}
            </ReactMarkdown>
          ) : (
            <p className="text-muted-foreground/50 italic text-sm">{placeholder}</p>
          )}
        </div>
      )}

      {/* Variable validation badges */}
      {requireVariables && variables && variables.length > 0 && (
        <div className="flex flex-wrap gap-3">
          {variablePresence.map((v) => (
            <StatusBadge key={v.token} label={v.label} active={v.present} />
          ))}
        </div>
      )}

      {/* Hint box */}
      {hint && (
        <div className="flex items-start gap-2 p-2 bg-slate-50 rounded-md dark:bg-slate-900 border">
          <IconInfoCircle className="w-4 h-4 text-amber-500 shrink-0 mt-0.5" />
          <p className="text-[11px] text-muted-foreground">{hint}</p>
        </div>
      )}
    </div>
  );
};

/**
 * Helper: returns true when all required variables are present in the prompt.
 */
export function allVariablesPresent(prompt: string, variables: PromptVariable[]): boolean {
  return variables.every((v) => prompt.includes(v.token));
}
