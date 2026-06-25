import { Input } from "@/components/ui/input";
import { SectionCard } from "@/components/ui/section-card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { TabsContent } from "@/components/ui/tabs";
import { IconFlask } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

import { FormRow } from "./form-row";

interface ExperimentTabProps {
  experimentKey: string;
  setExperimentKey: (value: string) => void;
  variantLabel: string;
  setVariantLabel: (value: string) => void;
  trafficWeight: number | null;
  setTrafficWeight: (value: number | null) => void;
  banditEnabled: boolean;
  setBanditEnabled: (value: boolean) => void;
  autoPromote: boolean;
  setAutoPromote: (value: boolean) => void;
  experimentSuccessMetric: string;
  setExperimentSuccessMetric: (value: string) => void;
}

/**
 * "A/B experiment" tab — A/B routing, traffic weight, Thompson-sampling
 * bandit, success metric, and champion-challenger auto-promotion. Lifted out
 * of the Settings tab onto its own tab so the experiment knobs (which most
 * flows never touch) don't crowd the core editing form.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function ChatFlowExperimentTab({
  experimentKey,
  setExperimentKey,
  variantLabel,
  setVariantLabel,
  trafficWeight,
  setTrafficWeight,
  banditEnabled,
  setBanditEnabled,
  autoPromote,
  setAutoPromote,
  experimentSuccessMetric,
  setExperimentSuccessMetric,
}: Readonly<ExperimentTabProps>) {
  const { t } = useTranslation();
  return (
    <TabsContent value="experiment" className="flex-1 min-h-0 overflow-y-auto">
      <div className="space-y-4 px-4 lg:px-6 py-2 pb-8">
        {/* ── Card: A/B experiment ── */}
        <SectionCard variant="amber">
          <SectionCard.Header
            icon={IconFlask}
            title={t("chatFlow.sections.experiment.title", {
              defaultValue: "A/B experiment",
            })}
            description={t("chatFlow.sections.experiment.description", {
              defaultValue:
                "Group this flow with sibling flows under the same experiment key — the router will reassign conversations across variants by traffic weight. Leave the key empty to disable A/B routing.",
            })}
          />
          <SectionCard.Content>
            <div className="space-y-6">
              <FormRow
                htmlFor="chat-flow-experiment-key"
                label={t("chatFlow.fields.experimentKey", { defaultValue: "Experiment key" })}
                description={t("chatFlow.fields.experimentKeyHint", {
                  defaultValue:
                    "Identifier shared across all variants of one A/B test (e.g. 'pricing-page-tone'). Stable: changing it ends the experiment and starts a new one.",
                })}
              >
                <Input
                  id="chat-flow-experiment-key"
                  value={experimentKey}
                  onChange={(event) => setExperimentKey(event.target.value)}
                  placeholder="e.g. programa-match-headline-2026q1"
                  maxLength={64}
                />
              </FormRow>

              <FormRow
                htmlFor="chat-flow-variant-label"
                label={t("chatFlow.fields.variantLabel", { defaultValue: "Variant label" })}
                description={t("chatFlow.fields.variantLabelHint", {
                  defaultValue:
                    "This flow's arm inside the experiment ('control', 'treatment_v2', 'warmer_tone'). Surfaces as the grouping dimension in the analytics dashboard.",
                })}
              >
                <Input
                  id="chat-flow-variant-label"
                  value={variantLabel}
                  onChange={(event) => setVariantLabel(event.target.value)}
                  placeholder="control"
                  maxLength={64}
                  disabled={!experimentKey.trim()}
                />
              </FormRow>

              <FormRow
                htmlFor="chat-flow-traffic-weight"
                label={t("chatFlow.fields.trafficWeight", { defaultValue: "Traffic weight" })}
                description={t("chatFlow.fields.trafficWeightHint", {
                  defaultValue:
                    "Relative weight (0-100) across variants. (50, 50) splits evenly; (90, 10) routes 90% to the first. Sums do not have to equal 100 — only ratios matter. 0 pauses this arm without deleting it.",
                })}
              >
                <Input
                  id="chat-flow-traffic-weight"
                  type="number"
                  min={0}
                  max={100}
                  value={trafficWeight ?? ""}
                  onChange={(event) => {
                    const raw = event.target.value;
                    if (raw === "") {
                      setTrafficWeight(null);
                      return;
                    }
                    const parsed = Number.parseInt(raw, 10);
                    if (!Number.isNaN(parsed)) {
                      setTrafficWeight(Math.max(0, Math.min(100, parsed)));
                    }
                  }}
                  placeholder="50"
                  disabled={!experimentKey.trim() || banditEnabled}
                />
              </FormRow>

              {/* T70 / §VII.8.a — Bandit (Thompson sampling) toggle. When
                  ANY variant in the experimentKey has this on, the engine
                  ignores traffic weights and routes via Thompson sampling
                  on observed conversion data. Disabled until the flow is
                  inside an experimentKey. */}
              <FormRow
                htmlFor="chat-flow-bandit-enabled"
                label={t("chatFlow.fields.banditEnabled", { defaultValue: "Bandit (Thompson sampling)" })}
                description={t("chatFlow.fields.banditEnabledHint", {
                  defaultValue:
                    "When enabled, the engine ignores traffic weight and adapts to observed conversion using Thompson sampling on the goal-achieved rate. Activate on at least one variant under the same experiment key to enable bandit mode.",
                })}
              >
                <Switch
                  id="chat-flow-bandit-enabled"
                  checked={banditEnabled}
                  onCheckedChange={setBanditEnabled}
                  disabled={!experimentKey.trim()}
                />
              </FormRow>

              {/* T241 / §D.11 — per-experiment success metric. The bandit
                  optimises for this conversion and the significance test
                  defaults to it. Disabled until the flow is inside an
                  experimentKey; declare it on any arm to score the whole
                  experiment on it. */}
              <FormRow
                htmlFor="chat-flow-success-metric"
                label={t("chatFlow.fields.successMetric", { defaultValue: "Success metric" })}
                description={t("chatFlow.fields.successMetricHint", {
                  defaultValue:
                    "Which conversion the bandit optimises for and the significance test scores. Goal achieved is the default; the others activate once the analytics store persists those signals.",
                })}
              >
                <Select
                  value={experimentSuccessMetric || "GOAL_ACHIEVED"}
                  onValueChange={setExperimentSuccessMetric}
                  disabled={!experimentKey.trim()}
                >
                  <SelectTrigger id="chat-flow-success-metric" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="GOAL_ACHIEVED">
                      {t("chatFlow.fields.successMetricGoalAchieved", { defaultValue: "Goal achieved" })}
                    </SelectItem>
                    <SelectItem value="HANDOFF_WHATSAPP">
                      {t("chatFlow.fields.successMetricHandoff", { defaultValue: "Handoff requested" })}
                    </SelectItem>
                    <SelectItem value="LEAD_EMAIL_CAPTURED">
                      {t("chatFlow.fields.successMetricLead", { defaultValue: "Lead email captured" })}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </FormRow>

              {/* T71 / §VII.8.b — Champion-challenger auto-promotion. When ANY
                  variant under the experimentKey opts in, the daily job
                  promotes the significant winner to 100% traffic and archives
                  the losers. Disabled until the flow is inside an
                  experimentKey. */}
              <FormRow
                htmlFor="chat-flow-auto-promote"
                label={t("chatFlow.fields.autoPromote", {
                  defaultValue: "Auto-promote winner (champion-challenger)",
                })}
                description={t("chatFlow.fields.autoPromoteHint", {
                  defaultValue:
                    "When enabled, a daily job checks statistical significance and, once a winner is declared, pins it to 100% of traffic and archives the losing variants. Leave off to keep manual control — you can still promote on demand from the significance report.",
                })}
              >
                <Switch
                  id="chat-flow-auto-promote"
                  checked={autoPromote}
                  onCheckedChange={setAutoPromote}
                  disabled={!experimentKey.trim()}
                />
              </FormRow>
            </div>
          </SectionCard.Content>
        </SectionCard>
      </div>
    </TabsContent>
  );
}
