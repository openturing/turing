import { usePersonas } from "@/api/queries/persona.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoFormSection, BentoHero } from "@/components/bento";
import {
  ContentFitDetails,
  FitBar,
} from "@/components/persona/content-fit-result.view";
import { PersonaSelectGrid } from "@/components/persona/persona-select-grid";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type { TurPersona } from "@/models/persona/persona.model.ts";
import type { TurPersonaSuggestionReport } from "@/models/persona/persona-suggestion.model.ts";
import { TurPersonaSuggestionService } from "@/services/persona/persona-suggestion.service";
import {
  IconAlertTriangle,
  IconRefresh,
  IconSparkles,
  IconTargetArrow,
  IconTrophy,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

const service = new TurPersonaSuggestionService();

/** True for AUDIENCE / BOTH personas — the only ones the evaluator can grade. */
function isAudience(p: TurPersona): boolean {
  return p.personaKind === "AUDIENCE" || p.personaKind === "BOTH";
}

/**
 * Bento persona auto-suggest — Block AG follow-up (T619). Re-adds T471's
 * "which persona is this content even *for*" tool, dropped in the console
 * retirement (T570), as a first-class bento launch surface reached from the
 * persona list header. Paste content, optionally restrict to a subset of
 * audience personas, and the backend batches the T467 content-fit evaluator and
 * returns the personas ranked best-fit first (red/amber/green bars à la T388,
 * reusing the shared {@link FitBar}/{@link ContentFitDetails} view). Frosted
 * {@link BentoFormSection} cards + a {@link BentoHero} replace the console
 * `SectionCard`/`SubPage` chrome of the original page.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function BentoPersonaSuggestPage() {
  const { t } = useTranslation();
  const { data: personas } = usePersonas();
  const audiencePersonas = useMemo(
    () => (personas ?? []).filter(isAudience),
    [personas],
  );

  const [content, setContent] = useState("");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [report, setReport] = useState<TurPersonaSuggestionReport | null>(null);
  const [busy, setBusy] = useState(false);

  const run = useCallback(
    async (regenerate: boolean) => {
      if (!content.trim()) {
        toast.error(t("forms.persona.suggest.noContent"));
        return;
      }
      setBusy(true);
      try {
        const ids = selectedIds.size > 0 ? [...selectedIds] : undefined;
        const result = await service.suggest(content, { personaIds: ids, regenerate });
        setReport(result);
        if (result.evaluatedCount === 0) {
          toast.error(t("forms.persona.suggest.noPersonas"));
        }
      } catch {
        toast.error(t("forms.persona.suggest.failed"));
      } finally {
        setBusy(false);
      }
    },
    [content, selectedIds, t],
  );

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_PERSONA_INSTANCE}
        backLabel={t("persona.title")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-violet-500 to-fuchsia-600 text-white shadow-md">
            <IconTargetArrow size={24} />
          </span>
        }
        title={t("forms.persona.suggest.title")}
        subtitle={t("forms.persona.suggest.desc")}
      />

      <div className="mt-6 flex flex-col gap-5">
        <BentoFormSection
          icon={IconTargetArrow}
          tone="violet"
          title={t("forms.persona.suggest.title")}
          description={t("forms.persona.suggest.desc")}
        >
          <label className="mb-1 block text-sm font-medium">
            {t("forms.persona.suggest.contentLabel")}
          </label>
          <Textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={8}
            placeholder={t("forms.persona.suggest.contentPlaceholder")}
          />

          {audiencePersonas.length > 0 && (
            <div className="mt-4">
              <p className="mb-2 text-xs text-muted-foreground">
                {t("forms.persona.suggest.restrictLabel")}
              </p>
              <PersonaSelectGrid
                personas={audiencePersonas
                  .filter((p) => p.id)
                  .map((p) => ({
                    id: p.id as string,
                    name: p.name,
                    subtitle: p.personaKind
                      ? t(`persona.match.kind.${p.personaKind}`)
                      : undefined,
                  }))}
                selectedIds={[...selectedIds]}
                onChange={(ids) => setSelectedIds(new Set(ids))}
              />
            </div>
          )}

          <div className="mt-4 flex flex-wrap items-center gap-2">
            <Button type="button" disabled={busy} onClick={() => run(false)}>
              <IconSparkles className="size-4" />
              {busy
                ? t("forms.persona.suggest.evaluating")
                : t("forms.persona.suggest.evaluate")}
            </Button>
            {report && (
              <Button
                type="button"
                variant="outline"
                disabled={busy}
                onClick={() => run(true)}
              >
                <IconRefresh className="size-4" />
                {t("forms.persona.suggest.regenerate")}
              </Button>
            )}
          </div>

          {audiencePersonas.length === 0 && personas && (
            <p className="mt-3 flex items-center gap-1 text-xs text-muted-foreground">
              <IconAlertTriangle className="size-3.5" />
              {t("forms.persona.suggest.noPersonasHint")}
            </p>
          )}
        </BentoFormSection>

        {report && report.rankings.length > 0 && (
          <BentoFormSection
            icon={IconTrophy}
            tone="emerald"
            title={t("forms.persona.suggest.rankingTitle")}
            description={t("forms.persona.suggest.considered", {
              count: report.evaluatedCount,
            })}
          >
            {!report.llmAvailable && (
              <p className="mb-3 flex items-center gap-1 text-xs text-muted-foreground">
                <IconAlertTriangle className="size-3.5" />
                {t("forms.persona.suggest.readabilityOnly")}
              </p>
            )}
            <ul className="flex flex-col gap-3">
              {report.rankings.map((s) => {
                const isBest = s.rank === 1;
                return (
                  <li
                    key={s.personaId}
                    className={`rounded-lg border p-3 ${
                      isBest ? "border-emerald-400 bg-emerald-500/5" : ""
                    }`}
                  >
                    <div className="mb-1 flex items-center gap-2">
                      <span className="w-7 shrink-0 text-sm font-semibold text-muted-foreground">
                        #{s.rank}
                      </span>
                      <Link
                        to={`${ROUTES.BENTO_PERSONA_INSTANCE}/${s.personaId}`}
                        className="font-medium hover:underline"
                      >
                        {s.personaName}
                      </Link>
                      {isBest && (
                        <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-400">
                          {t("forms.persona.suggest.bestFit")}
                        </span>
                      )}
                    </div>
                    <FitBar score={s.fitScore} />
                    <ContentFitDetails result={s.result} />
                  </li>
                );
              })}
            </ul>
          </BentoFormSection>
        )}
      </div>
    </>
  );
}
