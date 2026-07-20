"use client"
import type {
  TurLlmModelKind,
  TurLlmModelOption,
} from "@/models/llm/llm-model-option.model.ts"
import { cn } from "@/lib/utils"
import { useTranslation } from "react-i18next"

/**
 * Visual metadata for a model {@link TurLlmModelKind} (T751): a colour-coded
 * pill class + an i18n label key. The colours reuse the brand palette (CHAT =
 * blue→indigo family) with a distinct, accessible tone per kind in both light
 * and dark mode. Shared by {@code LlmModelMultiSelect} and {@code ModelCombobox}
 * so the badge + type filter look identical across every model picker.
 */
const KIND_META: Record<TurLlmModelKind, { badge: string; dot: string; labelKey: string }> = {
  CHAT: {
    badge: "bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300",
    dot: "bg-blue-500",
    labelKey: "forms.llm.modelKindChat",
  },
  EMBEDDING: {
    badge: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300",
    dot: "bg-emerald-500",
    labelKey: "forms.llm.modelKindEmbedding",
  },
  RERANK: {
    badge: "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300",
    dot: "bg-amber-500",
    labelKey: "forms.llm.modelKindRerank",
  },
  IMAGE: {
    badge: "bg-fuchsia-100 text-fuchsia-700 dark:bg-fuchsia-500/15 dark:text-fuchsia-300",
    dot: "bg-fuchsia-500",
    labelKey: "forms.llm.modelKindImage",
  },
  TRANSCRIPTION: {
    badge: "bg-sky-100 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300",
    dot: "bg-sky-500",
    labelKey: "forms.llm.modelKindTranscription",
  },
  SPEECH: {
    badge: "bg-violet-100 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300",
    dot: "bg-violet-500",
    labelKey: "forms.llm.modelKindSpeech",
  },
  VIDEO: {
    badge: "bg-rose-100 text-rose-700 dark:bg-rose-500/15 dark:text-rose-300",
    dot: "bg-rose-500",
    labelKey: "forms.llm.modelKindVideo",
  },
  MODERATION: {
    badge: "bg-slate-200 text-slate-700 dark:bg-slate-500/20 dark:text-slate-300",
    dot: "bg-slate-500",
    labelKey: "forms.llm.modelKindModeration",
  },
  UNKNOWN: {
    badge: "bg-muted text-muted-foreground",
    dot: "bg-muted-foreground",
    labelKey: "forms.llm.modelKindUnknown",
  },
}

/** Canonical display order for the type filter (most common kinds first). */
export const MODEL_KIND_ORDER: TurLlmModelKind[] = [
  "CHAT",
  "EMBEDDING",
  "RERANK",
  "IMAGE",
  "TRANSCRIPTION",
  "SPEECH",
  "VIDEO",
  "MODERATION",
  "UNKNOWN",
]

/** A model's kind, defaulting to UNKNOWN when the backend didn't classify it. */
export function resolveModelKind(option: Pick<TurLlmModelOption, "kind">): TurLlmModelKind {
  return option.kind ?? "UNKNOWN"
}

/** The i18n key for a kind's short label. */
export function modelKindLabelKey(kind: TurLlmModelKind): string {
  return KIND_META[kind].labelKey
}

/**
 * A small, colour-coded pill naming a model's {@link TurLlmModelKind} — the
 * answer to "what am I selecting?" on every model option and selected row.
 */
export function ModelKindBadge({
  kind,
  className,
}: {
  kind: TurLlmModelKind
  className?: string
}) {
  const { t } = useTranslation()
  const meta = KIND_META[kind]
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium",
        meta.badge,
        className,
      )}
    >
      {t(meta.labelKey)}
    </span>
  )
}

/** The coloured dot used inside the type-filter segmented control. */
export function ModelKindDot({ kind }: { kind: TurLlmModelKind }) {
  return <span className={cn("size-1.5 shrink-0 rounded-full", KIND_META[kind].dot)} />
}
