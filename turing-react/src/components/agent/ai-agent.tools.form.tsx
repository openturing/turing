"use client"
import { Checkbox } from "@/components/ui/checkbox"
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts"
import type { NativeToolGroup } from "@/models/genai/native-tool.model.ts"
import { TurAIAgentService } from "@/services/agent/ai-agent.service"
import { TurNativeToolService } from "@/services/genai/native-tool.service"
import { IconCheck, IconTool } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { toast } from "sonner"
import { GradientButton } from "../ui/gradient-button"
import { SectionCard } from "../ui/section-card"

const turAIAgentService = new TurAIAgentService();
const turNativeToolService = new TurNativeToolService();

interface Props {
  value: TurAIAgent;
}

function parseNativeTools(nativeTools?: string | null): Set<string> {
  if (!nativeTools) return new Set();
  return new Set(
    nativeTools.split(",").map((s) => s.trim()).filter(Boolean)
  );
}

function serializeNativeTools(tools: Set<string>): string {
  return Array.from(tools).sort().join(",");
}

export const AIAgentToolsForm: React.FC<Props> = ({ value }) => {
  const [toolGroups, setToolGroups] = useState<NativeToolGroup[]>([]);
  const [selectedTools, setSelectedTools] = useState<Set<string>>(new Set());
  const [savedTools, setSavedTools] = useState<Set<string>>(new Set());

  useEffect(() => {
    turNativeToolService.query().then(setToolGroups);
  }, []);

  useEffect(() => {
    const tools = parseNativeTools(value.nativeTools);
    setSelectedTools(tools);
    setSavedTools(tools);
  }, [value]);

  const isDirty = (() => {
    if (selectedTools.size !== savedTools.size) return true;
    for (const t of selectedTools) {
      if (!savedTools.has(t)) return true;
    }
    return false;
  })();

  function toggleTool(name: string, checked: boolean) {
    setSelectedTools((prev) => {
      const next = new Set(prev);
      if (checked) next.add(name);
      else next.delete(name);
      return next;
    });
  }

  function toggleGroup(group: NativeToolGroup, checked: boolean) {
    setSelectedTools((prev) => {
      const next = new Set(prev);
      for (const tool of group.tools) {
        if (checked) next.add(tool.name);
        else next.delete(tool.name);
      }
      return next;
    });
  }

  function isGroupFullySelected(group: NativeToolGroup) {
    return group.tools.every((t) => selectedTools.has(t.name));
  }

  function isGroupPartiallySelected(group: NativeToolGroup) {
    const count = group.tools.filter((t) => selectedTools.has(t.name)).length;
    return count > 0 && count < group.tools.length;
  }

  async function onSave() {
    const payload: TurAIAgent = {
      ...value,
      nativeTools: serializeNativeTools(selectedTools),
    };
    try {
      const result = await turAIAgentService.update(payload);
      if (result) {
        toast.success("Native tools updated");
        setSavedTools(new Set(selectedTools));
      } else {
        toast.error("Failed to update native tools");
      }
    } catch (error) {
      console.error("Save error", error);
      toast.error("Failed to update native tools");
    }
  }

  function onReset() {
    setSelectedTools(new Set(savedTools));
  }

  const totalTools = toolGroups.reduce((acc, g) => acc + g.tools.length, 0);

  return (
    <div className="px-6">
      <div className="space-y-4 py-8">
        <SectionCard variant="blue">
          <SectionCard.Header
            icon={IconTool}
            title="Available Native Tools"
            description="Select which built-in tool callings are available to this agent."
          />
          <SectionCard.Content>
            {toolGroups.length > 0 ? (
              <div className="space-y-4">
                {toolGroups.map((group) => {
                  const groupSelected = isGroupFullySelected(group);
                  const groupPartial = isGroupPartiallySelected(group);
                  return (
                    <div key={group.id} className="space-y-1.5">
                      {/* Group header */}
                      <label className="flex items-center gap-3 cursor-pointer py-1">
                        <Checkbox
                          checked={groupSelected ? true : groupPartial ? "indeterminate" : false}
                          onCheckedChange={(checked) => toggleGroup(group, !!checked)}
                        />
                        <div className="flex items-center gap-2">
                          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-blue-500/15 dark:bg-blue-500/25">
                            <IconTool className="size-4 text-blue-600 dark:text-blue-400" />
                          </div>
                          <span className="text-sm font-semibold">{group.title}</span>
                          <span className="text-[10px] text-muted-foreground/70">
                            {group.tools.filter((t) => selectedTools.has(t.name)).length}/{group.tools.length}
                          </span>
                        </div>
                      </label>

                      {/* Individual tools */}
                      <div className="ml-7 space-y-1">
                        {group.tools.map((tool) => {
                          const isSelected = selectedTools.has(tool.name);
                          return (
                            <label
                              key={tool.name}
                              className={`flex items-center gap-3 rounded-lg border px-3 py-2.5 cursor-pointer transition-all ${isSelected
                                ? "border-blue-500/40 bg-blue-500/5 dark:border-blue-400/30 dark:bg-blue-500/10"
                                : "border-border hover:border-blue-500/20 hover:bg-accent/30"
                                }`}
                            >
                              <Checkbox
                                checked={isSelected}
                                onCheckedChange={(checked) => toggleTool(tool.name, !!checked)}
                              />
                              <div className="flex flex-col min-w-0 flex-1">
                                <span className="text-sm font-medium font-mono truncate">{tool.name}</span>
                                {tool.description && (
                                  <span className="text-xs text-muted-foreground truncate">{tool.description}</span>
                                )}
                              </div>
                              {isSelected && (
                                <IconCheck className="size-4 text-blue-600 dark:text-blue-400 shrink-0" />
                              )}
                            </label>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="text-center py-8">
                <IconTool className="mx-auto size-10 text-muted-foreground/30 mb-3" />
                <p className="text-sm text-muted-foreground">No native tools available.</p>
              </div>
            )}

            {totalTools > 0 && (
              <div className="flex items-center gap-2 mt-3 pt-3 border-t">
                <div className={`flex items-center gap-1.5 px-2 py-1 rounded-full border text-[10px] font-bold transition-all ${selectedTools.size > 0
                  ? "bg-blue-500/10 text-blue-600 border-blue-500/20 dark:text-blue-400"
                  : "bg-slate-100 text-slate-400 border-slate-200 dark:bg-slate-800 dark:border-slate-700"
                  }`}>
                  <div className={`w-1.5 h-1.5 rounded-full ${selectedTools.size > 0
                    ? "bg-blue-500 animate-pulse"
                    : "bg-slate-300 dark:bg-slate-600"
                    }`} />
                  {selectedTools.size} / {totalTools} SELECTED
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
