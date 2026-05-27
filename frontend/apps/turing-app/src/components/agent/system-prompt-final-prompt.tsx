"use client"
import { SectionCard } from "@/components/ui/section-card";
import type { TurSystemPromptPreview, TurSystemPromptTool } from "@/models/agent/system-prompt.model";
import { IconClipboard, IconEye, IconFileText, IconPencil } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import ReactMarkdown from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";

/**
 * The verbatim FINAL system prompt for the previewed turn — persona + agent
 * prompt + MCP instructions + the selected chat flow's addendum, plus the
 * tool definitions the model receives alongside it. Rendered as formatted
 * Markdown by default (toggle to raw), with a copy button. Placed last on the
 * page so it reads as the bottom-line "this is what the model sees".
 *
 * @since 2026.3.1
 */

interface Props {
  readonly preview: TurSystemPromptPreview | undefined;
  readonly isLoading: boolean;
}

export function SystemPromptFinalPrompt({ preview, isLoading }: Props) {
  const { t } = useTranslation();
  const [mode, setMode] = useState<"preview" | "raw">("preview");

  const toolsHeading = t("aiAgent.systemPrompt.finalPrompt.toolsHeading", {
    defaultValue: "Tools available to the model",
  });
  const toolsNote = t("aiAgent.systemPrompt.finalPrompt.toolsNote", {
    defaultValue: "_Provided to the model as function/tool schemas (not literal prompt text)._",
  });

  const fullText = useMemo(() => {
    if (!preview) return "";
    return preview.assembledText + buildToolsMarkdown(preview.tools, toolsHeading, toolsNote);
  }, [preview, toolsHeading, toolsNote]);

  async function copyFull() {
    if (!fullText) return;
    try {
      await navigator.clipboard.writeText(fullText);
      toast.success(t("aiAgent.systemPrompt.finalPrompt.copied", { defaultValue: "Copied to clipboard" }));
    } catch {
      toast.error(t("aiAgent.systemPrompt.finalPrompt.copyFailed", { defaultValue: "Could not copy" }));
    }
  }

  return (
    <SectionCard variant="violet">
      <SectionCard.Header
        icon={IconFileText}
        title={t("aiAgent.systemPrompt.finalPrompt.title", { defaultValue: "Final prompt" })}
        description={t("aiAgent.systemPrompt.finalPrompt.description", {
          defaultValue:
            "The complete system message the model receives for the previewed turn, including the tool definitions sent alongside it.",
        })}
      />
      <SectionCard.Content>
        <div className="flex items-center justify-end gap-2 mb-2">
          <div className="flex items-center rounded-md border bg-muted/50 p-0.5 gap-0.5">
            <button
              type="button"
              onClick={() => setMode("preview")}
              className={`flex items-center gap-1 px-2.5 py-1 rounded text-xs font-medium transition-all ${
                mode === "preview" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <IconEye className="size-3.5" />
              {t("aiAgent.systemPrompt.finalPrompt.preview", { defaultValue: "Preview" })}
            </button>
            <button
              type="button"
              onClick={() => setMode("raw")}
              className={`flex items-center gap-1 px-2.5 py-1 rounded text-xs font-medium transition-all ${
                mode === "raw" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <IconPencil className="size-3.5" />
              {t("aiAgent.systemPrompt.finalPrompt.raw", { defaultValue: "Raw" })}
            </button>
          </div>
          <button
            type="button"
            onClick={copyFull}
            className="inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs text-muted-foreground hover:text-foreground hover:bg-accent/50 transition-colors"
          >
            <IconClipboard className="size-3.5" />
            {t("aiAgent.systemPrompt.finalPrompt.copy", { defaultValue: "Copy" })}
          </button>
        </div>

        {isLoading && (
          <p className="text-sm text-muted-foreground">{t("common.loading", { defaultValue: "Loading…" })}</p>
        )}

        {!isLoading && preview && mode === "raw" && (
          <pre className="max-h-[36rem] overflow-auto rounded-md border bg-muted/40 p-3 text-xs leading-relaxed whitespace-pre-wrap wrap-break-word font-mono">
            {fullText}
          </pre>
        )}

        {!isLoading && preview && mode === "preview" && (
          <div className="max-h-[36rem] overflow-y-auto rounded-md border bg-background px-3 py-2 prose prose-sm dark:prose-invert prose-neutral max-w-none break-words prose-p:my-2 prose-pre:my-2 prose-ul:my-2 prose-ol:my-2 prose-headings:my-3 prose-code:before:content-none prose-code:after:content-none prose-code:bg-muted prose-code:px-1 prose-code:py-0.5 prose-code:rounded prose-code:text-sm prose-pre:bg-muted prose-pre:border prose-pre:rounded-lg">
            <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
              {fullText}
            </ReactMarkdown>
          </div>
        )}
      </SectionCard.Content>
    </SectionCard>
  );
}

function buildToolsMarkdown(tools: TurSystemPromptTool[], heading: string, note: string): string {
  if (!tools || tools.length === 0) {
    return "";
  }
  const lines = tools.map(
    (tool) => `- **${tool.name}** _(${tool.source})_${tool.description ? `: ${tool.description}` : ""}`,
  );
  return `\n\n---\n\n## ${heading}\n\n${note}\n\n${lines.join("\n")}`;
}
