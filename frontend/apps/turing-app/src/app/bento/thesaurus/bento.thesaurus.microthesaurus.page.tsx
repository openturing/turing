import {
  useCreateMicrothesaurus,
  useDeleteMicrothesaurus,
  useMicrothesaurus,
  useThesaurusTerms,
  useUpdateMicrothesaurus,
} from "@/api/queries/thesaurus.queries";
import { ROUTES } from "@/app/routes.const";
import {
  BentoEntityShell,
  BentoFormSection,
  BentoSaveBar,
  type BentoIdentity,
  type BentoShellFormState,
} from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import { Input } from "@/components/ui/input";
import type { TurMicrothesaurus } from "@/models/kb/thesaurus.model";
import { IconBinaryTree2, IconLanguage, IconSitemap } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { ThesaurusTermTree } from "./thesaurus-term-tree";

type ShellMicro = TurMicrothesaurus & { title: string; description: string };

const BENTO_MICRO_FORM_ID = "bento-thesaurus-microthesaurus-form";

export default function BentoThesaurusMicrothesaurusPage() {
  const { kbId, microId } = useParams() as { kbId: string; microId: string };
  const { t } = useTranslation();

  const { data: micro, isError } = useMicrothesaurus(kbId, microId);
  const error = isError ? t("common.connectionError", { resource: t("thesaurus.microthesaurus") }) : null;

  const updateMutation = useUpdateMicrothesaurus(kbId);
  const deleteMutation = useDeleteMicrothesaurus(kbId);
  const navigate = useNavigate();

  function shell(entity: TurMicrothesaurus) {
    const shellEntity: ShellMicro = {
      ...entity,
      title: entity.name ?? "",
      description: entity.description ?? "",
    };
    return (
      <BentoEntityShell<ShellMicro>
        entity={shellEntity}
        isNew={false}
        headlineFallback={entity.name}
        eyebrow={t("thesaurus.microthesaurus")}
        listRoute={`${ROUTES.BENTO_THESAURUS}/${kbId}`}
        icon={IconSitemap}
        tone="indigo"
        formId={BENTO_MICRO_FORM_ID}
        feature={t("thesaurus.microthesaurus")}
        hideIcon
        onUpdate={(next) =>
          updateMutation.mutateAsync({ ...entity, name: next.title, description: next.description })
        }
        onDelete={async () => {
          const ok = await deleteMutation.mutateAsync(entity.id);
          if (ok) navigate(`${ROUTES.BENTO_THESAURUS}/${kbId}`);
          return ok;
        }}
      >
        {({ staged, onStateChange }) => (
          <>
            <MicroForm kbId={kbId} value={entity} staged={staged} onStateChange={onStateChange} />
            <BentoFormSection
              icon={IconBinaryTree2}
              tone="violet"
              title={t("thesaurus.termTree")}
              description={t("thesaurus.termTreeDesc")}
            >
              <TermTreeSection microId={entity.id} />
            </BentoFormSection>
          </>
        )}
      </BentoEntityShell>
    );
  }

  return (
    <LoadProvider
      checkIsNotUndefined={micro}
      error={error}
      tryAgainUrl={`${ROUTES.BENTO_THESAURUS}/${kbId}/m/${microId}`}
    >
      {micro && shell(micro)}
    </LoadProvider>
  );
}

function TermTreeSection({ microId }: { microId: string }) {
  const { data: terms } = useThesaurusTerms(microId);
  return <ThesaurusTermTree microId={microId} terms={terms ?? []} />;
}

interface MicroFormProps {
  kbId: string;
  value: TurMicrothesaurus;
  staged?: BentoIdentity;
  onStateChange?: (state: BentoShellFormState) => void;
}

/** Owns language + domain (name/description live in the hero) and the Save bar. */
function MicroForm({ kbId, value, staged, onStateChange }: MicroFormProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const updateMutation = useUpdateMicrothesaurus(kbId);
  // A microthesaurus is only ever created from the KB page, so this form is
  // update-only; leaving the create hook out keeps the surface simple.
  useCreateMicrothesaurus(kbId);

  const [language, setLanguage] = useState(value.language ?? "pt");
  const [domain, setDomain] = useState(value.domain ?? "GENERAL");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setLanguage(value.language ?? "pt");
    setDomain(value.domain ?? "GENERAL");
  }, [value]);

  const dirty = language !== (value.language ?? "pt") || domain !== (value.domain ?? "GENERAL");
  useEffect(() => {
    onStateChange?.({ isDirty: dirty, isSubmitting: submitting });
  }, [dirty, submitting, onStateChange]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await updateMutation.mutateAsync({
        ...value,
        name: staged?.title?.trim() || value.name,
        description: staged?.description ?? value.description,
        language: language.trim() || "pt",
        domain: domain.trim() || "GENERAL",
      });
      toast.success(t("forms.common.updated", { name: value.name, feature: t("thesaurus.microthesaurus") }));
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form id={BENTO_MICRO_FORM_ID} onSubmit={handleSubmit} className="flex flex-col gap-5">
      <div aria-hidden className="bento-save-bar-spacer" />
      <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
        <BentoSaveBar
          title={staged?.title?.trim() || value.name}
          disabled={!dirty}
          dirty={dirty}
          loading={submitting}
          onCancel={() => navigate(`${ROUTES.BENTO_THESAURUS}/${kbId}`)}
        />
      </div>

      <BentoFormSection
        icon={IconLanguage}
        tone="indigo"
        title={t("thesaurus.languageAndDomain")}
        description={t("thesaurus.languageAndDomainDesc")}
      >
        <div className="flex flex-wrap gap-4">
          <div className="flex w-32 flex-col gap-1">
            <label className="text-xs text-muted-foreground">{t("thesaurus.language")}</label>
            <Input value={language} onChange={(e) => setLanguage(e.target.value)} placeholder="pt" />
          </div>
          <div className="flex w-48 flex-col gap-1">
            <label className="text-xs text-muted-foreground">{t("thesaurus.domain")}</label>
            <Input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="GENERAL" />
          </div>
        </div>
      </BentoFormSection>
    </form>
  );
}
