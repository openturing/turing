import { useDeletePersona, useUpdatePersona } from "@/api/queries/persona.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoActionsMenu, BentoHero, BentoInlineEdit, BentoTile } from "@/components/bento";
import { DialogDelete } from "@/components/dialog.delete";
import type { TurPersona } from "@/models/persona/persona.model.ts";
import { toast } from "@viglet/viglet-design-system";
import {
  IconBook2,
  IconNotebook,
  IconPalette,
  IconReportAnalytics,
  IconSettings,
  IconSparkles,
  IconTrash,
  IconUserCircle,
  IconUsers,
} from "@tabler/icons-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, Navigate, useNavigate, useOutletContext } from "react-router-dom";
import type { BentoPersonaContext } from "./bento.persona.page";
import { PersonaLaunchBar } from "./persona.launch.bar";

const countTerms = (raw?: string | null) =>
  (raw ?? "").split("|").map((term) => term.trim()).filter(Boolean).length;

/**
 * Bento persona dashboard — Block AI / §XXXII.5 (T582). Reads like the AI-agent
 * dashboard: a {@link BentoHero} with inline-edit identity + status + delete,
 * the {@link PersonaLaunchBar}, then a frosted {@link BentoTile} mosaic where
 * each card is a doorway into a section (system instruction, style, general,
 * guidelines, and — for AUDIENCE/BOTH personas — audience, notebook, fit
 * report). A new persona has nothing to dashboard, so it redirects to the
 * general editor section.
 */
export default function BentoPersonaDashboard() {
  const { persona, isNew, setPersona } = useOutletContext<BentoPersonaContext>();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const updateMutation = useUpdatePersona();
  const deleteMutation = useDeletePersona();
  const [deleteOpen, setDeleteOpen] = useState(false);

  const enabled = Number(persona.enabled) === 1;
  const kind = persona.personaKind ?? "SPEAKER";
  const showAudience = kind === "AUDIENCE" || kind === "BOTH";
  const base = `${ROUTES.BENTO_PERSONA_INSTANCE}/${persona.id}`;

  const styleSummary = useMemo(() => {
    const parts: string[] = [];
    if (persona.tone) parts.push(t(`persona.tones.${persona.tone}`));
    if (persona.languageStyle) parts.push(t(`persona.languageStyles.${persona.languageStyle}`));
    parts.push(t("persona.dashboard.verbosityLevel", { level: persona.verbosity ?? 3, defaultValue: "Verbosity {{level}}/5" }));
    return parts.join(" · ");
  }, [persona.tone, persona.languageStyle, persona.verbosity, t]);

  const termCount = countTerms(persona.mandatoryTerms) + countTerms(persona.forbiddenTerms);

  /*
   * Dashboard identity edits aren't backed by a form/save bar, so each inline
   * edit commits immediately via the shared update mutation (iOS-style: change
   * it, it's saved) — mirrors the AI-agent dashboard. `setPersona` refreshes
   * the loader's copy so the tiles reflect the change without a refetch.
   */
  async function persistField(patch: Partial<TurPersona>) {
    const next = { ...persona, ...patch };
    try {
      const saved = await updateMutation.mutateAsync(next);
      setPersona(saved ?? next);
      toast.success(t("forms.common.updated", { name: next.name, feature: t("persona.title") }));
    } catch (err) {
      console.error("Failed to update persona field", err);
      toast.error(t("forms.common.notUpdated", { name: persona.name, feature: t("persona.title") }));
    }
  }

  async function onDelete() {
    try {
      if (await deleteMutation.mutateAsync(persona)) {
        toast.success(t("persona.deleted", { name: persona.name }));
        navigate(ROUTES.BENTO_PERSONA_INSTANCE);
      } else {
        toast.error(t("persona.notDeleted", { name: persona.name }));
      }
    } catch (err) {
      console.error("Failed to delete persona", err);
      toast.error(t("persona.notDeleted", { name: persona.name }));
    }
    setDeleteOpen(false);
  }

  if (isNew) {
    // A new persona has no data to dashboard — go straight to the editor.
    return <Navigate to="general" replace />;
  }

  return (
    <>
      <BentoHero
        eyebrow={
          <Link to={ROUTES.BENTO_PERSONA_INSTANCE} className="hover:text-foreground">
            {t("persona.title")}
          </Link>
        }
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-blue-500 to-indigo-600 text-white shadow-md">
            <IconUserCircle size={24} />
          </span>
        }
        title={
          <BentoInlineEdit
            value={persona.name}
            onSave={(name) => persistField({ name })}
            placeholder={t("persona.title")}
            className="text-3xl font-semibold tracking-tight md:text-4xl"
            ariaLabel={t("forms.common.name")}
          />
        }
        subtitle={
          <BentoInlineEdit
            value={persona.description ?? ""}
            onSave={(description) => persistField({ description })}
            multiline
            placeholder={t("forms.common.description")}
            className="max-w-2xl text-sm text-muted-foreground"
            ariaLabel={t("forms.common.description")}
          />
        }
        trailing={
          <>
            <button
              type="button"
              onClick={() => persistField({ enabled: enabled ? 0 : 1 })}
              aria-label={t("forms.common.enabled")}
              className={`bento-tile-clickable flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10px] uppercase tracking-wider transition-colors duration-200 ${
                enabled
                  ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-600 hover:bg-emerald-500/20 dark:text-emerald-400"
                  : "border-border bg-muted text-muted-foreground hover:bg-muted/80"
              }`}
            >
              <span className={`h-1.5 w-1.5 rounded-full ${enabled ? "bg-emerald-500 bento-pulse" : "bg-muted-foreground/60"}`} />
              {enabled ? t("forms.common.active", { defaultValue: "Active" }) : t("forms.common.idle", { defaultValue: "Idle" })}
            </button>
            <BentoActionsMenu
              actions={[
                {
                  label: t("forms.formActions.delete"),
                  icon: IconTrash,
                  tone: "destructive",
                  onSelect: () => setDeleteOpen(true),
                },
              ]}
            />
          </>
        }
      />

      <DialogDelete
        feature={t("persona.title")}
        name={persona.name}
        onDelete={onDelete}
        open={deleteOpen}
        setOpen={setDeleteOpen}
        trigger={<span className="hidden" aria-hidden />}
      />

      <PersonaLaunchBar persona={persona} />

      <div className="bento-grid grid auto-rows-[minmax(140px,auto)] grid-cols-2 gap-4 md:grid-cols-4 md:gap-5 lg:grid-cols-6">
        {/* HERO: System instruction — large 2x2 frosted tile. */}
        <BentoTile
          to={`${base}/system-instruction`}
          icon={IconSparkles}
          tone="violet"
          featured
          span="col-span-2 row-span-2 md:col-span-2 md:row-span-2 lg:col-span-3 lg:row-span-2"
          eyebrow={t("persona.sidebar.systemInstruction")}
          title={persona.systemInstruction?.trim()
            ? t("persona.dashboard.systemInstructionSet", { defaultValue: "Voice contract defined" })
            : t("persona.dashboard.systemInstructionEmpty", { defaultValue: "Set the voice contract" })}
        >
          <p className="line-clamp-5 text-sm text-muted-foreground">
            {persona.systemInstruction?.trim() || t("persona.dashboard.systemInstructionHint", {
              defaultValue: "The system instruction prepended to chats that adopt this persona.",
            })}
          </p>
        </BentoTile>

        <BentoTile
          to={`${base}/style`}
          icon={IconPalette}
          tone="emerald"
          span="col-span-2 row-span-1 md:col-span-2 md:row-span-1 lg:col-span-3 lg:row-span-1"
          eyebrow={t("persona.sidebar.style")}
          title={t("persona.dashboard.styleTitle", { defaultValue: "Style & voice" })}
        >
          <p className="text-sm text-muted-foreground">{styleSummary}</p>
        </BentoTile>

        <BentoTile
          to={`${base}/general`}
          icon={IconSettings}
          tone="blue"
          span="col-span-1 row-span-1 md:col-span-1 lg:col-span-1"
          eyebrow={t("persona.sidebar.general")}
          title={t(`persona.kinds.${kind}`)}
        />

        <BentoTile
          to={`${base}/guidelines`}
          icon={IconBook2}
          tone="amber"
          span="col-span-1 row-span-1 md:col-span-1 lg:col-span-2"
          eyebrow={t("persona.sidebar.guidelines")}
          title={t("persona.dashboard.guidelinesTitle", { defaultValue: "Guidelines" })}
        >
          <p className="text-sm text-muted-foreground">
            {termCount > 0
              ? t("persona.dashboard.termCount", {
                  mandatory: countTerms(persona.mandatoryTerms),
                  forbidden: countTerms(persona.forbiddenTerms),
                  defaultValue: "{{mandatory}} mandatory · {{forbidden}} forbidden",
                })
              : t("persona.dashboard.guidelinesHint", { defaultValue: "Mandatory and forbidden terms." })}
          </p>
        </BentoTile>

        {showAudience && (
          <>
            <BentoTile
              to={`${base}/audience`}
              icon={IconUsers}
              tone="indigo"
              span="col-span-2 row-span-1 md:col-span-2 lg:col-span-2"
              eyebrow={t("persona.sidebar.audience")}
              title={t("persona.dashboard.audienceTitle", { defaultValue: "Audience profile" })}
            >
              <p className="text-sm text-muted-foreground">
                {t("persona.dashboard.audienceHint", { defaultValue: "Who this persona reads for." })}
              </p>
            </BentoTile>

            <BentoTile
              to={`${base}/notebook`}
              icon={IconNotebook}
              tone="slate"
              span="col-span-1 row-span-1 md:col-span-1 lg:col-span-2"
              eyebrow={t("persona.sidebar.notebook")}
              title={t("persona.dashboard.notebookTitle", { defaultValue: "Evaluation notebook" })}
            >
              <p className="text-sm text-muted-foreground">
                {t("persona.dashboard.notebookHint", { defaultValue: "Sources used to score content fit." })}
              </p>
            </BentoTile>

            <BentoTile
              to={`${base}/fit-report`}
              icon={IconReportAnalytics}
              tone="rose"
              span="col-span-1 row-span-1 md:col-span-1 lg:col-span-2"
              eyebrow={t("persona.sidebar.fitReport")}
              title={t("persona.dashboard.fitReportTitle", { defaultValue: "Audience-fit report" })}
            >
              <p className="text-sm text-muted-foreground">
                {t("persona.dashboard.fitReportHint", { defaultValue: "How well content matches this audience." })}
              </p>
            </BentoTile>
          </>
        )}
      </div>
    </>
  );
}
