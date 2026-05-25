import {
  IconAlertTriangle,
  IconCheck,
  IconLoader2,
  IconSparkles,
  IconWand,
} from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

import {
  useCreateChatFlow,
  useGenerateChatFlowVariant,
} from "@/api/queries/chat-flow.queries";
import { ROUTES } from "@/app/routes.const";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type {
  TurChatFlow,
  TurChatFlowVariantResponse,
} from "@/models/agent/chat-flow.model";

interface ChatFlowVariantDialogProps {
  /** Source flow being varied. {@code null} closes the dialog. */
  source: TurChatFlow | null;
  agentId: string;
  onClose: () => void;
}

/**
 * T97 / §VII.11.g — chat-flow variant generator dialog.
 *
 * <p>Two phases inside one dialog:
 * <ol>
 *   <li><b>Brief</b>: author types a free-text directive ({@code "more casual
 *       tone, 30% shorter"}, {@code "translate to Spanish"}, {@code "for a
 *       teen audience"}) and an optional name for the resulting flow.</li>
 *   <li><b>Review</b>: after the LLM returns, the dialog shows the rewrite
 *       summary plus the count of nodes whose copy actually changed. The
 *       author either saves (POST {@code /chat-flow} via the standard create
 *       mutation, then navigates to the new flow's editor) or refines the
 *       brief and regenerates.</li>
 * </ol>
 *
 * <p>Nothing is persisted until the author clicks "Save and open" — the
 * backend returns an unpersisted candidate so iterating ({@code regenerate})
 * never pollutes the flow list.
 *
 * @since 2026.3.1
 */
export function ChatFlowVariantDialog({
  source,
  agentId,
  onClose,
}: Readonly<ChatFlowVariantDialogProps>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const variantMutation = useGenerateChatFlowVariant();
  const createMutation = useCreateChatFlow();

  const [instructions, setInstructions] = useState("");
  const [targetName, setTargetName] = useState("");
  const [candidate, setCandidate] = useState<TurChatFlowVariantResponse | null>(null);
  const [generating, setGenerating] = useState(false);
  const [saving, setSaving] = useState(false);

  // Reset whenever the dialog opens on a new source — stale candidate from a
  // prior session must not bleed into the next author's brief.
  useEffect(() => {
    if (source) {
      setInstructions("");
      setTargetName("");
      setCandidate(null);
      setGenerating(false);
      setSaving(false);
    }
  }, [source]);

  const handleGenerate = useCallback(async () => {
    if (!source?.id || !instructions.trim()) return;
    setGenerating(true);
    setCandidate(null);
    try {
      const response = await variantMutation.mutateAsync({
        agentId,
        flowId: source.id,
        request: {
          instructions: instructions.trim(),
          targetName: targetName.trim() || null,
        },
      });
      setCandidate(response);
      if (!response.success) {
        toast.error(
          response.error ??
            t("chatFlow.variant.generateFailed", {
              defaultValue: "Failed to generate variant.",
            }),
        );
      }
    } catch (error) {
      console.error("Chat flow variant generation failed", error);
      toast.error(
        t("chatFlow.variant.generateFailed", {
          defaultValue: "Failed to generate variant.",
        }),
      );
    } finally {
      setGenerating(false);
    }
  }, [agentId, instructions, source, targetName, t, variantMutation]);

  const handleSave = useCallback(async () => {
    if (!candidate?.success || !candidate.candidate) return;
    setSaving(true);
    try {
      const created = await createMutation.mutateAsync({
        agentId,
        flow: candidate.candidate,
      });
      toast.success(
        t("chatFlow.variant.saved", {
          defaultValue: "Variant '{{name}}' created (disabled — review and enable).",
          name: created.name,
        }),
      );
      onClose();
      if (created.id) {
        navigate(`${ROUTES.AI_AGENT_INSTANCE}/${agentId}/chat-flow/${created.id}`);
      }
    } catch (error) {
      console.error("Chat flow variant save failed", error);
      toast.error(
        t("chatFlow.variant.saveFailed", {
          defaultValue: "Failed to save variant.",
        }),
      );
    } finally {
      setSaving(false);
    }
  }, [agentId, candidate, createMutation, navigate, onClose, t]);

  const busy = generating || saving;
  const showCandidate = candidate?.success && candidate.candidate;
  const rewrittenCount = candidate?.rewrittenNodes?.length ?? 0;

  return (
    <Dialog
      open={source !== null}
      onOpenChange={(open) => {
        if (!open && !busy) onClose();
      }}
    >
      <DialogContent className="sm:max-w-2xl max-h-[85vh] flex flex-col">
        {source && (
          <>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <IconSparkles className="size-5 text-blue-600" />
                {t("chatFlow.variant.title", {
                  defaultValue: "Generate variant of '{{name}}'",
                  name: source.name,
                })}
              </DialogTitle>
              <DialogDescription>
                {t("chatFlow.variant.description", {
                  defaultValue:
                    "Describe the tone, length, or audience change you want. The LLM rewrites only the user-facing copy — node ids, edges, slots, and tool calls stay the same.",
                })}
              </DialogDescription>
            </DialogHeader>

            <div className="overflow-y-auto flex-1 -mx-1 px-1 py-2 space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="chat-flow-variant-instructions">
                  {t("chatFlow.variant.instructionsLabel", {
                    defaultValue: "Instructions",
                  })}
                </Label>
                <Textarea
                  id="chat-flow-variant-instructions"
                  rows={3}
                  value={instructions}
                  onChange={(event) => setInstructions(event.target.value)}
                  placeholder={t("chatFlow.variant.instructionsPlaceholder", {
                    defaultValue:
                      "e.g. 'more casual tone, 30% shorter, target Gen Z' or 'translate to Portuguese'",
                  })}
                  disabled={busy}
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="chat-flow-variant-name">
                  {t("chatFlow.variant.nameLabel", {
                    defaultValue: "New flow name (optional)",
                  })}
                </Label>
                <Input
                  id="chat-flow-variant-name"
                  value={targetName}
                  onChange={(event) => setTargetName(event.target.value)}
                  placeholder={t("chatFlow.variant.namePlaceholder", {
                    defaultValue: "{{name}} (variant)",
                    name: source.name,
                  })}
                  disabled={busy}
                />
              </div>

              {/* Result panel — empty until the LLM responds. */}
              {candidate && !candidate.success && (
                <div className="flex items-start gap-2 rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
                  <IconAlertTriangle className="size-4 mt-0.5 shrink-0" />
                  <div>
                    {candidate.error ??
                      t("chatFlow.variant.generateFailed", {
                        defaultValue: "Failed to generate variant.",
                      })}
                  </div>
                </div>
              )}

              {showCandidate && (
                <div className="rounded-lg border border-blue-500/40 bg-blue-500/5 p-3 space-y-2">
                  <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                    <IconCheck className="size-4" />
                    {t("chatFlow.variant.ready", {
                      defaultValue: "Variant ready",
                    })}
                  </div>
                  {candidate.summary && (
                    <p className="text-sm leading-relaxed">{candidate.summary}</p>
                  )}
                  <div className="text-xs text-muted-foreground">
                    {t("chatFlow.variant.rewrittenCount", {
                      defaultValue: "{{count}} node(s) rewritten.",
                      count: rewrittenCount,
                    })}
                  </div>
                  <p className="text-[11px] text-muted-foreground italic">
                    {t("chatFlow.variant.savedDisabledHint", {
                      defaultValue:
                        "Saved variants land disabled so you can review the rewrite in the editor before enabling.",
                    })}
                  </p>
                </div>
              )}
            </div>

            <DialogFooter>
              <Button variant="outline" onClick={onClose} disabled={busy}>
                {t("common.cancel")}
              </Button>
              <Button
                variant="outline"
                onClick={handleGenerate}
                disabled={busy || !instructions.trim()}
              >
                {generating ? (
                  <IconLoader2 className="size-4 animate-spin" />
                ) : (
                  <IconWand className="size-4" />
                )}
                {candidate
                  ? t("chatFlow.variant.regenerate", { defaultValue: "Regenerate" })
                  : t("chatFlow.variant.generate", { defaultValue: "Generate" })}
              </Button>
              <GradientButton
                onClick={handleSave}
                disabled={busy || !showCandidate}
              >
                {saving ? (
                  <IconLoader2 className="size-4 animate-spin" />
                ) : (
                  <IconSparkles className="size-4" />
                )}
                {t("chatFlow.variant.saveAndOpen", {
                  defaultValue: "Save and open",
                })}
              </GradientButton>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
