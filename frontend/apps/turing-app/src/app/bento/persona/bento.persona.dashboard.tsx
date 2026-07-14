import { useDeletePersona, useUpdatePersona } from "@/api/queries/persona.queries";
import { CHAT_INITIAL_PROMPT_KEY } from "@/app/console/chat/chat.types";
import { ROUTES } from "@/app/routes.const";
import { BentoActionsMenu, BentoHero, BentoInlineEdit, BentoTile } from "@/components/bento";
import { DialogDelete } from "@/components/dialog.delete";
import type { TurPersona } from "@/models/persona/persona.model.ts";
import { toast } from "@viglet/viglet-design-system";
import {
  IconFileSearch,
  IconMessageChatbot,
  IconSettings,
  IconTrash,
  IconUserCircle,
} from "@tabler/icons-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useOutletContext } from "react-router-dom";
import type { BentoPersonaContext } from "./bento.persona.page";
import { BentoPersonaCreateChooser } from "./bento.persona.create.chooser";

/**
 * Bento persona dashboard — Block AI / §XXXII.5 (T582). Reads like the AI-agent
 * dashboard: a {@link BentoHero} with inline-edit identity + status + delete,
 * then a frosted {@link BentoTile} mosaic. Because the persona is a CRUD whose
 * sections already live behind the editor's pill tabs, the mosaic no longer
 * duplicates one tile per tab — it surfaces a single **Configure** card that
 * opens the editor, plus the per-persona **actions as cards**: Open chat (T580,
 * always live) and Validate content (T584, live for AUDIENCE/BOTH, else a
 * disabled hint). A new persona has nothing to dashboard, so it redirects to
 * the general editor section.
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
  const canValidate = kind === "AUDIENCE" || kind === "BOTH";
  const base = `${ROUTES.BENTO_PERSONA_INSTANCE}/${persona.id}`;

  const styleSummary = useMemo(() => {
    const parts: string[] = [];
    if (persona.tone) parts.push(t(`persona.tones.${persona.tone}`));
    if (persona.languageStyle) parts.push(t(`persona.languageStyles.${persona.languageStyle}`));
    parts.push(t("persona.dashboard.verbosityLevel", { level: persona.verbosity ?? 3, defaultValue: "Verbosity {{level}}/5" }));
    return parts.join(" · ");
  }, [persona.tone, persona.languageStyle, persona.verbosity, t]);

  // "Open chat" clears any stale one-shot prompt before navigating (mirrors the
  // old launch bar), so it runs as an onClick action tile rather than a link.
  function openChat() {
    sessionStorage.removeItem(CHAT_INITIAL_PROMPT_KEY);
    navigate(`${ROUTES.BENTO_CHAT_PERSONA}/${persona.id}`);
  }

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
    // A new persona has no data to dashboard — offer the create-method chooser
    // (manual vs. from-audio) before opening the editor.
    return <BentoPersonaCreateChooser setPersona={setPersona} />;
  }

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_PERSONA_INSTANCE}
        backLabel={t("persona.title")}
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
              {enabled ? t("common.active", { defaultValue: "Active" }) : t("common.idle", { defaultValue: "Idle" })}
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

      <div className="bento-grid grid auto-rows-[minmax(140px,auto)] grid-cols-2 gap-4 md:grid-cols-4 md:gap-5 lg:grid-cols-6">
        {/* CONFIGURE (CRUD): one door into the editor, whose pill tabs already
            expose every section — so the dashboard doesn't duplicate a tile per
            tab. Featured 2x2 hero. */}
        <BentoTile
          to={`${base}/general`}
          icon={IconSettings}
          tone="violet"
          featured
          span="col-span-2 row-span-2 md:col-span-2 md:row-span-2 lg:col-span-3 lg:row-span-2"
          eyebrow={t(`persona.kinds.${kind}`)}
          title={t("persona.dashboard.configureTitle", { defaultValue: "Configure persona" })}
        >
          <p className="text-sm text-muted-foreground">
            {t("persona.dashboard.configureHint", {
              defaultValue: "Identity, system instruction, style, guidelines and audience.",
            })}
          </p>
          <p className="mt-2 text-sm text-muted-foreground">{styleSummary}</p>
        </BentoTile>

        {/* ACTION: Open chat — always live. */}
        <BentoTile
          onClick={openChat}
          icon={IconMessageChatbot}
          tone="blue"
          span="col-span-2 row-span-1 md:col-span-2 lg:col-span-3"
          eyebrow={t("persona.launch.title", { defaultValue: "Launch" })}
          title={t("persona.launch.openChat", { defaultValue: "Open chat" })}
        >
          <p className="text-sm text-muted-foreground">
            {t("persona.dashboard.chatHint", { defaultValue: "Talk to a chat that adopts this persona." })}
          </p>
        </BentoTile>

        {/* ACTION: Validate content — content-fit scoring, audience personas only. */}
        <BentoTile
          {...(canValidate
            ? { onClick: () => navigate(`${ROUTES.BENTO_PERSONA_VALIDATE}/${persona.id}`) }
            : { disabled: true, disabledHint: t("persona.launch.audienceOnly", { defaultValue: "Available for audience personas" }) })}
          icon={IconFileSearch}
          tone="emerald"
          span="col-span-2 row-span-1 md:col-span-2 lg:col-span-3"
          eyebrow={t("persona.launch.title", { defaultValue: "Launch" })}
          title={t("persona.launch.validateContent", { defaultValue: "Validate content" })}
        >
          <p className="text-sm text-muted-foreground">
            {t("persona.dashboard.validateHint", { defaultValue: "Score how well content fits this audience." })}
          </p>
        </BentoTile>
      </div>
    </>
  );
}
