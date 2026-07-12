import {
  useDeleteKnowledgeBase,
  useDeleteMicrothesaurus,
  useKnowledgeBase,
  useMicrothesauri,
  useUpdateKnowledgeBase,
} from "@/api/queries/thesaurus.queries";
import { ROUTES } from "@/app/routes.const";
import {
  BentoEntityShell,
  BentoFormSection,
  type BentoShellFormState,
} from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import { GradientButton } from "@/components/ui/gradient-button";
import type { TurKnowledgeBase } from "@/models/kb/thesaurus.model";
import { IconLanguage, IconPlus, IconSitemap, IconTrash } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import { ThesaurusMicroWizardDialog, ThesaurusNewWizard } from "./thesaurus-create-wizard";

type ShellKb = TurKnowledgeBase & { title: string; description: string };

const BENTO_THESAURUS_KB_FORM_ID = "bento-thesaurus-kb-form";

export default function BentoThesaurusKbPage() {
  const { kbId } = useParams() as { kbId: string };
  const { t } = useTranslation();
  const isNew = kbId === "new";

  const { data: kb, isError } = useKnowledgeBase(isNew ? undefined : kbId);
  const error = isError ? t("common.connectionError", { resource: t("thesaurus.title") }) : null;

  const updateMutation = useUpdateKnowledgeBase();
  const deleteMutation = useDeleteKnowledgeBase();

  // New thesaurus is now a guided wizard (name/description → first
  // microthesaurus), not a bare inline-edit detail screen.
  if (isNew) {
    return <ThesaurusNewWizard />;
  }

  function shell(entity: TurKnowledgeBase) {
    const shellEntity: ShellKb = {
      ...entity,
      title: entity.name ?? "",
      description: entity.description ?? "",
    };
    return (
      <BentoEntityShell<ShellKb>
        entity={shellEntity}
        isNew={false}
        headlineFallback={entity.name}
        eyebrow={t("thesaurus.title")}
        listRoute={ROUTES.BENTO_THESAURUS}
        icon={IconSitemap}
        tone="violet"
        formId={BENTO_THESAURUS_KB_FORM_ID}
        feature={t("thesaurus.knowledgeBase")}
        hideIcon
        // Name/description auto-save on blur and microthesauri are created via
        // the wizard button — there is no submittable form, so the header
        // "Save changes" button would sit permanently disabled. Hide it.
        autosaveOnly
        onUpdate={(next) =>
          updateMutation.mutateAsync({ ...entity, name: next.title, description: next.description })
        }
        onDelete={() => deleteMutation.mutateAsync(entity)}
      >
        {({ onStateChange }) => (
          <>
            <KbAutosaveMarker onStateChange={onStateChange} />
            <MicrothesauriSection kbId={kbId} />
          </>
        )}
      </BentoEntityShell>
    );
  }

  return (
    <LoadProvider checkIsNotUndefined={kb} error={error} tryAgainUrl={`${ROUTES.BENTO_THESAURUS}/${kbId}`}>
      {kb && shell(kb)}
    </LoadProvider>
  );
}

/**
 * The KB detail page has no submittable body form (identity auto-saves; the
 * microthesaurus list self-commits). It only needs to keep the shell's dirty
 * state clean so the — now hidden — Save button never claims pending changes.
 */
function KbAutosaveMarker({ onStateChange }: Readonly<{ onStateChange: (s: BentoShellFormState) => void }>) {
  useEffect(() => {
    onStateChange({ isDirty: false, isSubmitting: false });
  }, [onStateChange]);
  return <form id={BENTO_THESAURUS_KB_FORM_ID} />;
}

/** Lists the KB's microthesauri and opens the unified creation wizard. */
function MicrothesauriSection({ kbId }: Readonly<{ kbId: string }>) {
  const { t } = useTranslation();
  const { data: items } = useMicrothesauri(kbId);
  const deleteMutation = useDeleteMicrothesaurus(kbId);
  const [wizardOpen, setWizardOpen] = useState(false);

  return (
    <BentoFormSection
      icon={IconLanguage}
      tone="indigo"
      title={t("thesaurus.microthesauri")}
      description={t("thesaurus.microthesauriDesc")}
      trailing={
        <GradientButton type="button" className="gap-1" onClick={() => setWizardOpen(true)}>
          <IconPlus size={16} />
          {t("thesaurus.newMicrothesaurus")}
        </GradientButton>
      }
    >
      <div className="flex flex-col gap-2">
        {(items ?? []).map((m) => (
          <div
            key={m.id}
            className="bento-tile bento-glass flex items-center justify-between gap-3 rounded-2xl border border-border/60 px-4 py-3"
          >
            <Link to={`${ROUTES.BENTO_THESAURUS}/${kbId}/m/${m.id}`} className="flex min-w-0 flex-1 items-center gap-3">
              <IconSitemap size={18} className="shrink-0 text-indigo-500" />
              <span className="min-w-0">
                <span className="block truncate font-medium">{m.name}</span>
                <span className="block truncate text-xs text-muted-foreground">
                  {m.language} · {m.domain}
                  {typeof m.termCount === "number" ? ` · ${t("thesaurus.termCount", { count: m.termCount })}` : ""}
                </span>
              </span>
            </Link>
            <button
              type="button"
              aria-label={t("common.delete")}
              className="shrink-0 rounded-lg p-1.5 text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
              onClick={() => deleteMutation.mutate(m.id)}
            >
              <IconTrash size={16} />
            </button>
          </div>
        ))}
        {(items ?? []).length === 0 && (
          <p className="text-sm text-muted-foreground">{t("thesaurus.noMicrothesauriWizard")}</p>
        )}
      </div>

      <ThesaurusMicroWizardDialog kbId={kbId} open={wizardOpen} onOpenChange={setWizardOpen} />
    </BentoFormSection>
  );
}
