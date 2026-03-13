"use client"
import { Checkbox } from "@/components/ui/checkbox"
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts"
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts"
import { TurAIAgentService } from "@/services/agent/ai-agent.service"
import { TurLLMInstanceService } from "@/services/llm/llm.service"
import { IconCheck, IconCpu2 } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { toast } from "sonner"
import { GradientButton } from "../ui/gradient-button"
import { SectionCard } from "../ui/section-card"

const turAIAgentService = new TurAIAgentService();
const turLLMInstanceService = new TurLLMInstanceService();

interface Props {
  value: TurAIAgent;
}

export const AIAgentLlmForm: React.FC<Props> = ({ value }) => {
  const [llmInstances, setLlmInstances] = useState<TurLLMInstance[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    turLLMInstanceService.query().then(setLlmInstances);
  }, []);

  useEffect(() => {
    const ids = new Set((value.llmInstances ?? []).map((i) => i.id));
    setSelectedIds(ids);
    setSavedIds(ids);
  }, [value]);

  const isDirty = (() => {
    if (selectedIds.size !== savedIds.size) return true;
    for (const id of selectedIds) {
      if (!savedIds.has(id)) return true;
    }
    return false;
  })();

  function toggle(id: string, checked: boolean) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  async function onSave() {
    const selected = llmInstances.filter((i) => selectedIds.has(i.id));
    const payload: TurAIAgent = { ...value, llmInstances: selected };
    try {
      const result = await turAIAgentService.update(payload);
      if (result) {
        toast.success("Language models updated");
        setSavedIds(new Set(selectedIds));
      } else {
        toast.error("Failed to update language models");
      }
    } catch (error) {
      console.error("Save error", error);
      toast.error("Failed to update language models");
    }
  }

  function onReset() {
    setSelectedIds(new Set(savedIds));
  }

  return (
    <div className="px-6">
      <div className="space-y-4 py-8">
        <SectionCard variant="violet">
          <SectionCard.Header
            icon={IconCpu2}
            title="Available Language Models"
            description="Select which language models this agent can use for conversations."
          />
          <SectionCard.Content>
            {llmInstances.length > 0 ? (
              <div className="space-y-2">
                {llmInstances.map((llm) => {
                  const isSelected = selectedIds.has(llm.id);
                  return (
                    <label
                      key={llm.id}
                      className={`flex items-center gap-4 rounded-lg border p-4 cursor-pointer transition-all ${isSelected
                        ? "border-violet-500/40 bg-violet-500/5 dark:border-violet-400/30 dark:bg-violet-500/10"
                        : "border-border hover:border-violet-500/20 hover:bg-accent/30"
                        }`}
                    >
                      <Checkbox
                        checked={isSelected}
                        onCheckedChange={(checked) => toggle(llm.id, !!checked)}
                      />
                      <div className="flex flex-1 items-center gap-3 min-w-0">
                        <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg transition-colors ${isSelected
                          ? "bg-violet-500/15 dark:bg-violet-500/25"
                          : "bg-muted"
                          }`}>
                          <IconCpu2 className={`size-5 ${isSelected
                            ? "text-violet-600 dark:text-violet-400"
                            : "text-muted-foreground"
                            }`} />
                        </div>
                        <div className="flex flex-col min-w-0">
                          <span className="text-sm font-medium truncate">{llm.title}</span>
                          {llm.description && (
                            <span className="text-xs text-muted-foreground truncate">{llm.description}</span>
                          )}
                          {llm.turLLMVendor && (
                            <span className="text-[10px] text-muted-foreground/70 mt-0.5">
                              {llm.turLLMVendor.title}{llm.modelName ? ` / ${llm.modelName}` : ""}
                            </span>
                          )}
                        </div>
                      </div>
                      {isSelected && (
                        <IconCheck className="size-4 text-violet-600 dark:text-violet-400 shrink-0" />
                      )}
                    </label>
                  );
                })}
              </div>
            ) : (
              <div className="text-center py-8">
                <IconCpu2 className="mx-auto size-10 text-muted-foreground/30 mb-3" />
                <p className="text-sm text-muted-foreground">No language models available.</p>
                <p className="text-xs text-muted-foreground/70 mt-1">Create a Language Model instance first.</p>
              </div>
            )}

            {llmInstances.length > 0 && (
              <div className="flex items-center gap-2 mt-3 pt-3 border-t">
                <div className={`flex items-center gap-1.5 px-2 py-1 rounded-full border text-[10px] font-bold transition-all ${selectedIds.size > 0
                  ? "bg-violet-500/10 text-violet-600 border-violet-500/20 dark:text-violet-400"
                  : "bg-slate-100 text-slate-400 border-slate-200 dark:bg-slate-800 dark:border-slate-700"
                  }`}>
                  <div className={`w-1.5 h-1.5 rounded-full ${selectedIds.size > 0
                    ? "bg-violet-500 animate-pulse"
                    : "bg-slate-300 dark:bg-slate-600"
                    }`} />
                  {selectedIds.size} SELECTED
                </div>
              </div>
            )}
          </SectionCard.Content>
        </SectionCard>

        <div className="flex items-center justify-end gap-3 pt-4 border-t">
          <GradientButton type="button" variant="outline" onClick={onReset} disabled={!isDirty}>
            Reset
          </GradientButton>
          <GradientButton type="button" onClick={onSave} disabled={!isDirty}>
            Save Changes
          </GradientButton>
        </div>
      </div>
    </div>
  )
}
