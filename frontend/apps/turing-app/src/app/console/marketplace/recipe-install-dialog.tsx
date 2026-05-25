import { useAiAgents } from "@/api/queries/ai-agent.queries";
import {
  useChatFlowRecipe,
  useInstallChatFlowRecipe,
} from "@/api/queries/chat-flow-recipe.queries";
import { ROUTES } from "@/app/routes.const";
import { Badge } from "@/components/ui/badge";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import type { TurChatFlowRecipeSummary } from "@/models/agent/chat-flow-recipe.model";
import {
  IconAlertTriangle,
  IconArrowsRightLeft,
  IconBolt,
  IconBox,
  IconLoader2,
  IconPackageImport,
  IconSparkles,
} from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

interface RecipeInstallDialogProps {
  recipe: TurChatFlowRecipeSummary | null;
  onClose: () => void;
}

/**
 * T96 / §VII.11.f — preview + install dialog for a Turing Recipe.
 *
 * <p>Two phases:
 * <ol>
 *   <li>Preview: shows the full recipe (description + bundle: every flow,
 *       persona, slot the install will create) so the operator sees what
 *       they're getting before committing.</li>
 *   <li>Pick agent + install: operator chooses a destination AI agent from
 *       a dropdown (recipes are agent-agnostic — same catalog, multiple
 *       targets). On success, navigates to the first created flow's editor
 *       so the operator can immediately tweak it.</li>
 * </ol>
 *
 * <p>The "Open in editor" navigation is the bridge to T95 / T93 / T94 —
 * the freshly-installed flow lands on the editor where the live preview
 * (T95), the inheritance card (T93), the lint panel (T94) and the
 * conflict panel (T91) all kick in.
 *
 * @since 2026.3.1
 */
export function RecipeInstallDialog({
  recipe,
  onClose,
}: Readonly<RecipeInstallDialogProps>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data: fullRecipe, isLoading: recipeLoading } = useChatFlowRecipe(
    recipe?.id,
  );
  const { data: agents, isLoading: agentsLoading } = useAiAgents();
  const installMutation = useInstallChatFlowRecipe();

  const [selectedAgentId, setSelectedAgentId] = useState<string | undefined>();
  const [installing, setInstalling] = useState(false);

  const onInstall = async () => {
    if (!recipe || !selectedAgentId) return;
    setInstalling(true);
    try {
      const created = await installMutation.mutateAsync({
        agentId: selectedAgentId,
        recipeId: recipe.id,
      });
      toast.success(
        t("marketplace.recipeInstalled", {
          defaultValue: "Recipe '{{name}}' installed: {{count}} flow(s) created.",
          name: recipe.name,
          count: created.length,
        }),
      );
      onClose();
      if (created.length > 0) {
        navigate(
          `${ROUTES.AI_AGENT_INSTANCE}/${selectedAgentId}/chat-flow/${created[0].id}`,
        );
      }
    } catch (err) {
      console.error("Recipe install failed", err);
      toast.error(
        t("marketplace.recipeInstallFailed", {
          defaultValue: "Failed to install recipe '{{name}}'.",
          name: recipe.name,
        }),
      );
    } finally {
      setInstalling(false);
    }
  };

  return (
    <Dialog
      open={recipe !== null}
      onOpenChange={(open) => {
        if (!open && !installing) onClose();
      }}
    >
      <DialogContent className="sm:max-w-2xl max-h-[85vh] flex flex-col">
        {recipe && (
          <>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <IconSparkles className="size-5 text-blue-600" />
                {recipe.name}
              </DialogTitle>
              <DialogDescription className="flex items-center gap-2 pt-1">
                <Badge variant="secondary" className="capitalize">
                  {recipe.vertical}
                </Badge>
                <span className="text-xs">v{recipe.version}</span>
                {recipe.tags.map((tag) => (
                  <Badge key={tag} variant="outline" className="text-[10px]">
                    {tag}
                  </Badge>
                ))}
              </DialogDescription>
            </DialogHeader>

            <div className="overflow-y-auto flex-1 -mx-1 px-1 py-2 space-y-4">
              <p className="text-sm leading-relaxed text-muted-foreground">
                {recipe.description}
              </p>

              {/* What will be created */}
              <div className="rounded-lg border border-border/60 bg-muted/30 p-3 space-y-2">
                <div className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                  {t("marketplace.recipeWhatYouGet", {
                    defaultValue: "What this recipe installs",
                  })}
                </div>
                <div className="flex flex-wrap gap-2">
                  <BundleCount
                    icon={IconBox}
                    label={t("marketplace.recipeFlows", {
                      count: recipe.flowCount,
                      defaultValue: "{{count}} flow(s)",
                    })}
                  />
                  {recipe.slotCount > 0 && (
                    <BundleCount
                      icon={IconArrowsRightLeft}
                      label={t("marketplace.recipeSlots", {
                        count: recipe.slotCount,
                        defaultValue: "{{count}} slot(s)",
                      })}
                    />
                  )}
                  {recipe.personaCount > 0 && (
                    <BundleCount
                      icon={IconBolt}
                      label={t("marketplace.recipePersonas", {
                        count: recipe.personaCount,
                        defaultValue: "{{count}} persona(s)",
                      })}
                    />
                  )}
                </div>
              </div>

              {/* Bundle preview — list the flows + slots so operators
                  don't install blind. */}
              {recipeLoading ? (
                <div className="space-y-2">
                  <Skeleton className="h-6 w-1/2" />
                  <Skeleton className="h-4 w-full" />
                  <Skeleton className="h-4 w-5/6" />
                </div>
              ) : (
                fullRecipe && (
                  <div className="space-y-3">
                    {fullRecipe.bundle.map((item, index) => (
                      <div
                        key={index}
                        className="rounded-md border border-border/60 bg-background/50 p-3 space-y-1.5"
                      >
                        <div className="text-sm font-medium">
                          {item.chatFlow.name}
                        </div>
                        {item.chatFlow.description && (
                          <div className="text-xs text-muted-foreground leading-relaxed">
                            {item.chatFlow.description}
                          </div>
                        )}
                        {item.slots && item.slots.length > 0 && (
                          <div className="flex flex-wrap gap-1 pt-1">
                            {item.slots.map((s) => (
                              <Badge
                                key={s.name}
                                variant="outline"
                                className="font-mono text-[10px]"
                              >
                                {s.name}
                              </Badge>
                            ))}
                          </div>
                        )}
                        {item.chatFlow.slotInheritanceJson && (
                          <div className="flex items-center gap-1 pt-1 text-[11px] text-blue-600 dark:text-blue-400">
                            <IconArrowsRightLeft className="size-3" />
                            {t("marketplace.recipeInherits", {
                              defaultValue:
                                "Inherits slots from earlier flows on the same conversation",
                            })}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )
              )}

              {/* Target agent picker */}
              <div className="space-y-1.5 pt-1">
                <label
                  htmlFor="recipe-install-target-agent"
                  className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground"
                >
                  {t("marketplace.recipeTargetAgent", {
                    defaultValue: "Install on AI agent",
                  })}
                </label>
                {agentsLoading ? (
                  <Skeleton className="h-10 w-full" />
                ) : agents && agents.length > 0 ? (
                  <Select
                    value={selectedAgentId}
                    onValueChange={setSelectedAgentId}
                  >
                    <SelectTrigger id="recipe-install-target-agent">
                      <SelectValue
                        placeholder={t("marketplace.recipeTargetAgentPlaceholder", {
                          defaultValue: "Pick an agent…",
                        })}
                      />
                    </SelectTrigger>
                    <SelectContent>
                      {agents.map((agent) => (
                        <SelectItem key={agent.id} value={agent.id ?? ""}>
                          {agent.title || agent.id}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                ) : (
                  <div className="flex items-center gap-2 rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
                    <IconAlertTriangle className="size-4" />
                    {t("marketplace.recipeNoAgents", {
                      defaultValue:
                        "No AI agents found — create one first, then install this recipe.",
                    })}
                  </div>
                )}
              </div>
            </div>

            <DialogFooter>
              <Button
                variant="outline"
                onClick={onClose}
                disabled={installing}
              >
                {t("common.cancel")}
              </Button>
              <GradientButton
                onClick={onInstall}
                disabled={installing || !selectedAgentId}
              >
                {installing ? (
                  <IconLoader2 className="size-4 animate-spin" />
                ) : (
                  <IconPackageImport className="size-4" />
                )}
                {installing
                  ? t("marketplace.installing", { defaultValue: "Installing…" })
                  : t("marketplace.install", { defaultValue: "Install" })}
              </GradientButton>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

function BundleCount({
  icon: Icon,
  label,
}: Readonly<{
  icon: React.ComponentType<{ className?: string }>;
  label: string;
}>) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-border/60 bg-background px-2.5 py-1 text-xs text-muted-foreground">
      <Icon className="size-3.5" />
      {label}
    </span>
  );
}
