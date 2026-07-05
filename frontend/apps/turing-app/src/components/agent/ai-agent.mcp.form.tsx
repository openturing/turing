"use client"
import { useUpdateAiAgent } from "@/api/queries/ai-agent.queries"
import { useMcpServers } from "@/api/queries/mcp-server.queries"
import { Checkbox } from "@/components/ui/checkbox"
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts"
import type { TurMcpServer } from "@/models/mcp/mcp-server.model.ts"
import { IconCheck, IconServer2, IconWorldBolt } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { GradientButton } from "../ui/gradient-button"
import { GradientSwitch } from "../ui/gradient-switch"
import { SectionCard } from "../ui/section-card"
import { BentoFormSection } from "../bento"

interface Props {
  value: TurAIAgent;
  /** Render inside the bento shell (frosted BentoFormSection) instead of the console SectionCard. */
  chrome?: "console" | "bento";
}

export const AIAgentMcpForm: React.FC<Props> = ({ value, chrome = "console" }) => {
  const { t } = useTranslation();
  const { data: mcpServers = [] } = useMcpServers();
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set());
  const [federation, setFederation] = useState(false);
  const [savedFederation, setSavedFederation] = useState(false);
  const updateMutation = useUpdateAiAgent();

  useEffect(() => {
    const ids = new Set((value.mcpServers ?? []).map((s) => s.id));
    setSelectedIds(ids);
    setSavedIds(ids);
    setFederation(value.mcpNativeFederation ?? false);
    setSavedFederation(value.mcpNativeFederation ?? false);
  }, [value]);

  const isDirty = (() => {
    if (federation !== savedFederation) return true;
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
    const selected = mcpServers.filter((s) => selectedIds.has(s.id));
    const payload: TurAIAgent = { ...value, mcpServers: selected, mcpNativeFederation: federation };
    try {
      const result = await updateMutation.mutateAsync(payload);
      if (result) {
        toast.success(t("forms.agentMcp.updated"));
        setSavedIds(new Set(selectedIds));
        setSavedFederation(federation);
      } else {
        toast.error(t("forms.agentMcp.updateFailed"));
      }
    } catch (error) {
      console.error("Save error", error);
      toast.error(t("forms.agentMcp.updateFailed"));
    }
  }

  function onReset() {
    setSelectedIds(new Set(savedIds));
    setFederation(savedFederation);
  }

  function getConnectionLabel(mcp: TurMcpServer) {
    if (mcp.connectionType === "HTTP") return mcp.url ?? "HTTP";
    if (mcp.connectionType === "COMMAND") return mcp.command ?? "Command";
    return mcp.connectionType;
  }

  const Section = ({ children }: { readonly children: React.ReactNode }) =>
    chrome === "bento" ? (
      <BentoFormSection icon={IconServer2} tone="amber" title={t("forms.agentMcp.available")} description={t("forms.agentMcp.availableDesc")}>
        {children}
      </BentoFormSection>
    ) : (
      <SectionCard variant="cyan">
        <SectionCard.Header
          icon={IconServer2}
          title={t("forms.agentMcp.available")}
          description={t("forms.agentMcp.availableDesc")}
        />
        <SectionCard.Content>{children}</SectionCard.Content>
      </SectionCard>
    );

  const FederationSection = ({ children }: { readonly children: React.ReactNode }) =>
    chrome === "bento" ? (
      <section className="bento-tile bento-glass relative flex flex-col gap-5 overflow-hidden rounded-3xl p-5 md:p-6">
        {children}
      </section>
    ) : (
      <SectionCard variant="cyan">
        <SectionCard.Content>{children}</SectionCard.Content>
      </SectionCard>
    );

  return (
    <div className={chrome === "bento" ? "space-y-4 md:space-y-5" : "px-6"}>
      <div className={chrome === "bento" ? "space-y-4 md:space-y-5" : "space-y-4 py-8"}>
        <Section>
            {mcpServers.length > 0 ? (
              <div className="space-y-2">
                {mcpServers.map((mcp) => {
                  const isSelected = selectedIds.has(mcp.id);
                  return (
                    <label
                      key={mcp.id}
                      className={`flex items-center gap-4 rounded-lg border p-4 cursor-pointer transition-all ${isSelected
                        ? "border-cyan-500/40 bg-cyan-500/5 dark:border-cyan-400/30 dark:bg-cyan-500/10"
                        : "border-border hover:border-cyan-500/20 hover:bg-accent/30"
                        }`}
                    >
                      <Checkbox
                        checked={isSelected}
                        onCheckedChange={(checked) => toggle(mcp.id, !!checked)}
                      />
                      <div className="flex flex-1 items-center gap-3 min-w-0">
                        <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg transition-colors ${isSelected
                          ? "bg-cyan-500/15 dark:bg-cyan-500/25"
                          : "bg-muted"
                          }`}>
                          <IconServer2 className={`size-5 ${isSelected
                            ? "text-cyan-600 dark:text-cyan-400"
                            : "text-muted-foreground"
                            }`} />
                        </div>
                        <div className="flex flex-col min-w-0">
                          <span className="text-sm font-medium truncate">{mcp.title}</span>
                          {mcp.description && (
                            <span className="text-xs text-muted-foreground truncate">{mcp.description}</span>
                          )}
                          <div className="flex items-center gap-2 mt-1">
                            <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium border ${mcp.connectionType === "HTTP"
                              ? "bg-blue-500/10 text-blue-600 border-blue-500/20 dark:text-blue-400"
                              : "bg-amber-500/10 text-amber-600 border-amber-500/20 dark:text-amber-400"
                              }`}>
                              {mcp.connectionType}
                            </span>
                            <span className="text-[10px] text-muted-foreground/70 truncate">
                              {getConnectionLabel(mcp)}
                            </span>
                          </div>
                        </div>
                      </div>
                      {isSelected && (
                        <IconCheck className="size-4 text-cyan-600 dark:text-cyan-400 shrink-0" />
                      )}
                    </label>
                  );
                })}
              </div>
            ) : (
              <div className="text-center py-8">
                <IconServer2 className="mx-auto size-10 text-muted-foreground/30 mb-3" />
                <p className="text-sm text-muted-foreground">{t("forms.agentMcp.noServers")}</p>
                <p className="text-xs text-muted-foreground/70 mt-1">{t("forms.agentMcp.createFirst")}</p>
              </div>
            )}

            {mcpServers.length > 0 && (
              <div className="flex items-center gap-2 mt-3 pt-3 border-t">
                <div className={`flex items-center gap-1.5 px-2 py-1 rounded-full border text-[10px] font-bold transition-all ${selectedIds.size > 0
                  ? "bg-cyan-500/10 text-cyan-600 border-cyan-500/20 dark:text-cyan-400"
                  : "bg-slate-100 text-slate-400 border-slate-200 dark:bg-slate-800 dark:border-slate-700"
                  }`}>
                  <div className={`w-1.5 h-1.5 rounded-full ${selectedIds.size > 0
                    ? "bg-cyan-500 animate-pulse"
                    : "bg-slate-300 dark:bg-slate-600"
                    }`} />
                  {selectedIds.size} {t("forms.common.selected")}
                </div>
              </div>
            )}
        </Section>

        <FederationSection>
            <label className="flex items-start gap-4 cursor-pointer">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-cyan-500/15 dark:bg-cyan-500/25">
                <IconWorldBolt className="size-5 text-cyan-600 dark:text-cyan-400" />
              </div>
              <div className="flex flex-col min-w-0 flex-1">
                <span className="text-sm font-medium">{t("forms.agentMcp.federation")}</span>
                <span className="text-xs text-muted-foreground mt-0.5">
                  {t("forms.agentMcp.federationDesc")}
                </span>
              </div>
              <GradientSwitch
                checked={federation}
                onCheckedChange={(checked) => setFederation(!!checked)}
              />
            </label>
        </FederationSection>

        <div className="flex items-center justify-end gap-3 pt-4 border-t">
          <GradientButton type="button" variant="outline" onClick={onReset} disabled={!isDirty}>
            {t("forms.common.reset")}
          </GradientButton>
          <GradientButton type="button" onClick={onSave} disabled={!isDirty}>
            {t("forms.common.saveChanges")}
          </GradientButton>
        </div>
      </div>
    </div>
  )
}
