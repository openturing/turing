import { ROUTES } from "@/app/routes.const";
import { BentoFormSection, BentoHero } from "@/components/bento";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import type { TurPersona } from "@/models/persona/persona.model";
import { TurPersonaCohortService } from "@/services/persona/persona-cohort.service";
import {
  IconArrowRight,
  IconLoader2,
  IconRefresh,
  IconSparkles,
  IconTrash,
  IconUsersGroup,
  IconWand,
} from "@tabler/icons-react";
import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

const service = new TurPersonaCohortService();

/** The five OCEAN facets rendered as compact bars on a draft card. */
const OCEAN_FACETS: { key: keyof TurPersona; labelKey: string }[] = [
  { key: "openness", labelKey: "persona.cohort.facet.openness" },
  { key: "conscientiousness", labelKey: "persona.cohort.facet.conscientiousness" },
  { key: "extraversion", labelKey: "persona.cohort.facet.extraversion" },
  { key: "agreeableness", labelKey: "persona.cohort.facet.agreeableness" },
  { key: "neuroticism", labelKey: "persona.cohort.facet.neuroticism" },
];

/**
 * Audience cohort synthesis (Block AW / §XLVI.5, T731). A one-paragraph audience
 * brief → a diverse set of persona **drafts** with spread OCEAN facets, reusing the
 * AI-authoring strict-schema path. Nothing is auto-saved: each draft is reviewed in
 * the grid (keep → opens the existing persona review form pre-filled; discard →
 * drops it). The saved personas then populate a research study's audience.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function BentoPersonaCohortPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [brief, setBrief] = useState("");
  const [count, setCount] = useState(5);
  const [loading, setLoading] = useState(false);
  const [drafts, setDrafts] = useState<TurPersona[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const synthesize = useCallback(
    async (regenerate: boolean) => {
      if (!brief.trim() || loading) return;
      setLoading(true);
      setError(null);
      try {
        const result = await service.synthesize({ brief: brief.trim(), count, regenerate });
        if (result.success) {
          setDrafts(result.personas);
        } else {
          setError(result.error ?? t("persona.cohort.failed"));
          setDrafts(null);
        }
      } catch {
        setError(t("persona.cohort.failed"));
      } finally {
        setLoading(false);
      }
    },
    [brief, count, loading, t],
  );

  const keep = useCallback(
    (draft: TurPersona) => {
      navigate(`${ROUTES.BENTO_PERSONA_INSTANCE}/new`, { state: { draft } });
    },
    [navigate],
  );

  const discard = useCallback((index: number) => {
    setDrafts((prev) => (prev ? prev.filter((_, i) => i !== index) : prev));
  }, []);

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_PERSONA_RESEARCH}
        backLabel={t("persona.cohort.back")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-violet-600 to-fuchsia-600 text-white shadow-md">
            <IconUsersGroup size={24} />
          </span>
        }
        title={t("persona.cohort.title")}
        subtitle={t("persona.cohort.subtitle")}
      />

      <div className="flex flex-col gap-5">
        <BentoFormSection
          icon={IconWand}
          tone="violet"
          title={t("persona.cohort.briefTitle")}
          description={t("persona.cohort.briefDesc")}
        >
          <div className="flex flex-col gap-4">
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{t("persona.cohort.briefLabel")}</span>
              <Textarea
                value={brief}
                onChange={(e) => setBrief(e.target.value)}
                rows={4}
                placeholder={t("persona.cohort.briefPlaceholder")}
              />
            </label>
            <div className="flex flex-wrap items-end gap-3">
              <label className="flex w-32 flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.cohort.countLabel")}</span>
                <Input
                  type="number"
                  min={1}
                  max={12}
                  value={count}
                  onChange={(e) => setCount(Math.min(12, Math.max(1, Number(e.target.value) || 5)))}
                />
              </label>
              <Button onClick={() => synthesize(false)} disabled={!brief.trim() || loading} className="gap-2">
                {loading ? <IconLoader2 className="size-4 animate-spin" /> : <IconSparkles className="size-4" />}
                {t("persona.cohort.synthesize")}
              </Button>
              {drafts && !loading && (
                <Button variant="outline" onClick={() => synthesize(true)} disabled={loading} className="gap-2">
                  <IconRefresh className="size-4" />
                  {t("persona.cohort.regenerate")}
                </Button>
              )}
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <p className="text-xs text-muted-foreground">{t("persona.cohort.neverSaved")}</p>
          </div>
        </BentoFormSection>

        {drafts && (
          <BentoFormSection
            icon={IconUsersGroup}
            tone="rose"
            title={t("persona.cohort.reviewTitle")}
            description={t("persona.cohort.reviewDesc")}
            trailing={<Badge variant="secondary">{drafts.length}</Badge>}
          >
            {drafts.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t("persona.cohort.allDiscarded")}</p>
            ) : (
              <div className="grid gap-3 lg:grid-cols-2">
                {drafts.map((draft, index) => (
                  <CohortCard
                    key={`${draft.name}-${index}`}
                    draft={draft}
                    onKeep={() => keep(draft)}
                    onDiscard={() => discard(index)}
                  />
                ))}
              </div>
            )}
          </BentoFormSection>
        )}
      </div>
    </>
  );
}

function CohortCard({
  draft,
  onKeep,
  onDiscard,
}: Readonly<{ draft: TurPersona; onKeep: () => void; onDiscard: () => void }>) {
  const { t } = useTranslation();
  return (
    <div className="bento-tile bento-glass flex flex-col gap-3 rounded-2xl p-4">
      <div className="flex items-start gap-3">
        <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-linear-to-br from-violet-600 to-fuchsia-600 text-xs font-bold text-white">
          {(draft.name || "?").slice(0, 2).toUpperCase()}
        </span>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-semibold" title={draft.name}>
            {draft.name}
          </div>
          <p className="text-xs text-muted-foreground">{draft.description}</p>
        </div>
      </div>

      <div className="flex flex-col gap-1">
        {OCEAN_FACETS.map((facet) => {
          const value = draft[facet.key] as number | null | undefined;
          if (value === null || value === undefined) return null;
          return (
            <div key={facet.key as string} className="flex items-center gap-2">
              <span className="w-28 shrink-0 text-[11px] text-muted-foreground">{t(facet.labelKey)}</span>
              <span className="relative h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                <span className="absolute inset-y-0 left-0 rounded-full bg-violet-500" style={{ width: `${value}%` }} />
              </span>
              <span className="w-7 shrink-0 text-right text-[11px] text-muted-foreground">{value}</span>
            </div>
          );
        })}
      </div>

      <div className="flex items-center gap-2">
        {draft.tone && (
          <Badge variant="outline" className="text-[10px]">{draft.tone}</Badge>
        )}
        {draft.audience?.primaryLanguage && (
          <Badge variant="outline" className="text-[10px]">{draft.audience.primaryLanguage}</Badge>
        )}
      </div>

      <div className="flex items-center justify-between gap-2">
        <Button size="sm" className="gap-1.5" onClick={onKeep}>
          {t("persona.cohort.keep")}
          <IconArrowRight className="size-4" />
        </Button>
        <Button size="icon" variant="ghost" aria-label={t("persona.cohort.discard")} onClick={onDiscard}>
          <IconTrash className="size-4 text-red-600" />
        </Button>
      </div>
    </div>
  );
}
