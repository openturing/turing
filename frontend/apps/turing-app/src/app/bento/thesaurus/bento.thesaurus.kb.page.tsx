import {
  useCreateFromDraft,
  useCreateKnowledgeBase,
  useCreateMicrothesaurus,
  useDeleteKnowledgeBase,
  useDeleteMicrothesaurus,
  useGenerateThesaurusDraft,
  useImportSeed,
  useKnowledgeBase,
  useMicrothesauri,
  useThesaurusSeeds,
  useUpdateKnowledgeBase,
  useUploadAuthorityFile,
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
import { GradientButton } from "@/components/ui/gradient-button";
import type {
  TurKnowledgeBase,
  TurMicrothesaurus,
  TurThesaurusDraft,
} from "@/models/kb/thesaurus.model";
import {
  IconDownload,
  IconLanguage,
  IconLibrary,
  IconPlus,
  IconSitemap,
  IconSparkles,
  IconTrash,
  IconUpload,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useRef, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router-dom";

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

  function shell(entity: TurKnowledgeBase, headlineFallback: string) {
    const shellEntity: ShellKb = {
      ...entity,
      title: entity.name ?? "",
      description: entity.description ?? "",
    };
    return (
      <BentoEntityShell<ShellKb>
        entity={shellEntity}
        isNew={isNew}
        headlineFallback={headlineFallback}
        eyebrow={t("thesaurus.title")}
        listRoute={ROUTES.BENTO_THESAURUS}
        icon={IconSitemap}
        tone="violet"
        formId={BENTO_THESAURUS_KB_FORM_ID}
        feature={t("thesaurus.knowledgeBase")}
        hideIcon
        onUpdate={
          isNew
            ? undefined
            : (next) => updateMutation.mutateAsync({ ...entity, name: next.title, description: next.description })
        }
        onDelete={isNew ? undefined : () => deleteMutation.mutateAsync(entity)}
      >
        {({ staged, onStateChange }) => (
          <>
            <KbForm isNew={isNew} value={entity} staged={staged} onStateChange={onStateChange} />
            {!isNew && <MicrothesauriSection kbId={kbId} />}
            {!isNew && <GenerateSection kbId={kbId} />}
            {!isNew && <SeedImportSection kbId={kbId} />}
          </>
        )}
      </BentoEntityShell>
    );
  }

  if (isNew) {
    return shell({ id: "", name: "" }, t("thesaurus.newKnowledgeBase"));
  }

  return (
    <LoadProvider checkIsNotUndefined={kb} error={error} tryAgainUrl={`${ROUTES.BENTO_THESAURUS}/${kbId}`}>
      {kb && shell(kb, kb.name)}
    </LoadProvider>
  );
}

interface KbFormProps {
  isNew: boolean;
  value: TurKnowledgeBase;
  staged?: BentoIdentity;
  onStateChange?: (state: BentoShellFormState) => void;
}

/** KB has only name + description (both in the hero); this just owns the Save bar. */
function KbForm({ isNew, value, staged, onStateChange }: KbFormProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const createMutation = useCreateKnowledgeBase();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    onStateChange?.({ isDirty: isNew, isSubmitting: submitting });
  }, [isNew, submitting, onStateChange]);

  const stagedTitle = staged?.title?.trim() ?? "";
  const titleMissing = !stagedTitle;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (titleMissing) {
      toast.error(t("thesaurus.nameRequired"));
      return;
    }
    setSubmitting(true);
    try {
      const created = await createMutation.mutateAsync({
        ...value,
        name: stagedTitle,
        description: staged?.description ?? "",
      });
      toast.success(t("forms.common.saved", { name: created.name, feature: t("thesaurus.knowledgeBase") }));
      navigate(`${ROUTES.BENTO_THESAURUS}/${created.id}`);
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    } finally {
      setSubmitting(false);
    }
  }

  if (!isNew) {
    return <form id={BENTO_THESAURUS_KB_FORM_ID} />;
  }
  return (
    <form id={BENTO_THESAURUS_KB_FORM_ID} onSubmit={handleSubmit} className="flex flex-col gap-5">
      <div aria-hidden className="bento-save-bar-spacer" />
      <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
        <BentoSaveBar
          title={stagedTitle || t("thesaurus.newKnowledgeBase")}
          disabled={titleMissing}
          titleMissing={titleMissing}
          dirty
          loading={submitting}
          onCancel={() => navigate(ROUTES.BENTO_THESAURUS)}
        />
      </div>
    </form>
  );
}

/** Lists the KB's microthesauri and offers an inline "add microthesaurus" row. */
function MicrothesauriSection({ kbId }: { kbId: string }) {
  const { t } = useTranslation();
  const { data: items } = useMicrothesauri(kbId);
  const createMutation = useCreateMicrothesaurus(kbId);
  const deleteMutation = useDeleteMicrothesaurus(kbId);

  const [name, setName] = useState("");
  const [language, setLanguage] = useState("pt");
  const [domain, setDomain] = useState("GENERAL");

  async function add() {
    if (!name.trim()) {
      toast.error(t("thesaurus.nameRequired"));
      return;
    }
    try {
      await createMutation.mutateAsync({
        id: "",
        name: name.trim(),
        language: language.trim() || "pt",
        domain: domain.trim() || "GENERAL",
      } as TurMicrothesaurus);
      setName("");
      toast.success(t("thesaurus.microthesaurusAdded"));
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <BentoFormSection
      icon={IconLanguage}
      tone="indigo"
      title={t("thesaurus.microthesauri")}
      description={t("thesaurus.microthesauriDesc")}
    >
      <div className="flex flex-col gap-2">
        {(items ?? []).map((m) => (
          <div
            key={m.id}
            className="bento-tile bento-glass flex items-center justify-between gap-3 rounded-2xl border border-border/60 px-4 py-3"
          >
            <Link
              to={`${ROUTES.BENTO_THESAURUS}/${kbId}/m/${m.id}`}
              className="flex min-w-0 flex-1 items-center gap-3"
            >
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
          <p className="text-sm text-muted-foreground">{t("thesaurus.noMicrothesauri")}</p>
        )}
      </div>

      <div className="mt-4 flex flex-wrap items-end gap-2">
        <div className="flex flex-1 flex-col gap-1">
          <label className="text-xs text-muted-foreground">{t("thesaurus.microthesaurusName")}</label>
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder={t("thesaurus.microthesaurusName")} />
        </div>
        <div className="flex w-24 flex-col gap-1">
          <label className="text-xs text-muted-foreground">{t("thesaurus.language")}</label>
          <Input value={language} onChange={(e) => setLanguage(e.target.value)} placeholder="pt" />
        </div>
        <div className="flex w-40 flex-col gap-1">
          <label className="text-xs text-muted-foreground">{t("thesaurus.domain")}</label>
          <Input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="GENERAL" />
        </div>
        <GradientButton type="button" onClick={add} className="gap-1">
          <IconPlus size={16} />
          {t("thesaurus.addMicrothesaurus")}
        </GradientButton>
      </div>
    </BentoFormSection>
  );
}

/**
 * T675 — draft a microthesaurus with the LLM, review it, then persist. The draft
 * is never auto-saved: Generate shows a read-only tree preview and only "Create"
 * materialises it (reusing the T673 import path server-side).
 */
function GenerateSection({ kbId }: { kbId: string }) {
  const { t } = useTranslation();
  const generate = useGenerateThesaurusDraft(kbId);
  const create = useCreateFromDraft(kbId);

  const [domain, setDomain] = useState("");
  const [language, setLanguage] = useState("pt");
  const [guidance, setGuidance] = useState("");
  const [draft, setDraft] = useState<TurThesaurusDraft | null>(null);

  async function runGenerate() {
    if (!domain.trim()) {
      toast.error(t("thesaurus.generateDomainRequired"));
      return;
    }
    try {
      const result = await generate.mutateAsync({
        domain: domain.trim(),
        language: language.trim() || "pt",
        guidance: guidance.trim() || undefined,
      });
      setDraft(result);
    } catch (err) {
      console.error(err);
      toast.error(t("thesaurus.generateFailed"));
    }
  }

  async function accept() {
    if (!draft) return;
    try {
      const created = await create.mutateAsync(draft);
      toast.success(t("thesaurus.seedImported", { name: created.name }));
      setDraft(null);
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <BentoFormSection
      icon={IconSparkles}
      tone="violet"
      title={t("thesaurus.generateTitle")}
      description={t("thesaurus.generateDesc")}
    >
      <div className="flex flex-wrap items-end gap-2">
        <div className="flex flex-1 flex-col gap-1">
          <label className="text-xs text-muted-foreground">{t("thesaurus.domain")}</label>
          <Input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="EDUCATION" />
        </div>
        <div className="flex w-24 flex-col gap-1">
          <label className="text-xs text-muted-foreground">{t("thesaurus.language")}</label>
          <Input value={language} onChange={(e) => setLanguage(e.target.value)} placeholder="pt" />
        </div>
      </div>
      <div className="mt-2 flex flex-col gap-1">
        <label className="text-xs text-muted-foreground">{t("thesaurus.generateGuidance")}</label>
        <Input
          value={guidance}
          onChange={(e) => setGuidance(e.target.value)}
          placeholder={t("thesaurus.generateGuidancePlaceholder")}
        />
      </div>
      <div className="mt-3 flex items-center gap-3">
        <GradientButton type="button" className="gap-1" disabled={generate.isPending} onClick={runGenerate}>
          <IconSparkles size={16} />
          {generate.isPending ? t("thesaurus.generating") : t("thesaurus.generate")}
        </GradientButton>
        <span className="text-xs text-muted-foreground">{t("thesaurus.generateHint")}</span>
      </div>

      {draft && (
        <div className="bento-fade-in mt-4 rounded-2xl border border-border/60 bg-card/40 p-4 backdrop-blur">
          <div className="mb-2 flex items-center justify-between gap-3">
            <span className="min-w-0">
              <span className="block truncate font-medium">{draft.name}</span>
              <span className="block truncate text-xs text-muted-foreground">
                {draft.language} · {draft.domain} · {t("thesaurus.termCount", { count: draft.terms.length })}
              </span>
            </span>
            <div className="flex shrink-0 items-center gap-2">
              <button
                type="button"
                className="rounded-lg px-2 py-1 text-xs text-muted-foreground hover:bg-muted/50"
                onClick={() => setDraft(null)}
              >
                {t("thesaurus.discard")}
              </button>
              <GradientButton type="button" className="gap-1" disabled={create.isPending} onClick={accept}>
                <IconPlus size={16} />
                {t("thesaurus.createFromDraft")}
              </GradientButton>
            </div>
          </div>
          <DraftPreview draft={draft} />
        </div>
      )}
    </BentoFormSection>
  );
}

/** Read-only indented preview of a generated draft, built from the soft broader spine. */
function DraftPreview({ draft }: { draft: TurThesaurusDraft }) {
  const byId = new Map(draft.terms.map((term) => [term.id, term]));
  const depthOf = (id: string): number => {
    let depth = 0;
    let current = byId.get(id);
    const seen = new Set<string>();
    while (current?.broader && byId.has(current.broader) && !seen.has(current.id)) {
      seen.add(current.id);
      depth += 1;
      current = byId.get(current.broader);
    }
    return depth;
  };
  // Roots first, then a stable pre-order-ish grouping by depth chain.
  const ordered = [...draft.terms].sort((a, b) => depthOf(a.id) - depthOf(b.id));
  return (
    <ul className="flex max-h-72 flex-col gap-1 overflow-y-auto text-sm">
      {ordered.map((term) => (
        <li key={term.id} style={{ paddingLeft: `${depthOf(term.id) * 16}px` }} className="flex items-center gap-2">
          <IconSitemap size={14} className="shrink-0 text-violet-500" />
          <span className="truncate">{term.label}</span>
          {term.related && term.related.length > 0 && (
            <span className="shrink-0 rounded-full bg-violet-500/10 px-1.5 text-[10px] text-violet-500">
              {term.related.length} RT
            </span>
          )}
        </li>
      ))}
    </ul>
  );
}

/** Browse the bundled seed library + import, or upload a Turing Thesaurus XML. */
function SeedImportSection({ kbId }: { kbId: string }) {
  const { t } = useTranslation();
  const { data: seeds } = useThesaurusSeeds();
  const importSeed = useImportSeed(kbId);
  const upload = useUploadAuthorityFile(kbId);
  const fileRef = useRef<HTMLInputElement>(null);

  async function doImportSeed(seedId: string, name: string) {
    try {
      await importSeed.mutateAsync(seedId);
      toast.success(t("thesaurus.seedImported", { name }));
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  async function onFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const created = await upload.mutateAsync({ file });
      toast.success(t("thesaurus.seedImported", { name: created.name }));
    } catch (err) {
      console.error(err);
      toast.error(t("thesaurus.uploadFailed"));
    } finally {
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  return (
    <BentoFormSection
      icon={IconLibrary}
      tone="emerald"
      title={t("thesaurus.library")}
      description={t("thesaurus.libraryDesc")}
    >
      <div className="flex flex-col gap-2">
        {(seeds ?? []).map((seed) => (
          <div
            key={seed.id}
            className="bento-tile bento-glass flex items-center justify-between gap-3 rounded-2xl border border-border/60 px-4 py-3"
          >
            <span className="min-w-0">
              <span className="block truncate font-medium">{seed.name}</span>
              <span className="block truncate text-xs text-muted-foreground">
                {seed.language} · {seed.domain}
              </span>
            </span>
            <GradientButton
              type="button"
              className="shrink-0 gap-1"
              disabled={importSeed.isPending}
              onClick={() => doImportSeed(seed.id, seed.name)}
            >
              <IconDownload size={16} />
              {t("thesaurus.import")}
            </GradientButton>
          </div>
        ))}
        {(seeds ?? []).length === 0 && (
          <p className="text-sm text-muted-foreground">{t("thesaurus.noSeeds")}</p>
        )}
      </div>

      <div className="mt-4 flex items-center gap-3 border-t border-border/50 pt-4">
        <input
          ref={fileRef}
          type="file"
          accept=".xml,text/xml,application/xml"
          className="hidden"
          onChange={onFile}
        />
        <GradientButton
          type="button"
          className="gap-1"
          disabled={upload.isPending}
          onClick={() => fileRef.current?.click()}
        >
          <IconUpload size={16} />
          {t("thesaurus.uploadXml")}
        </GradientButton>
        <span className="text-xs text-muted-foreground">{t("thesaurus.uploadXmlHint")}</span>
      </div>
    </BentoFormSection>
  );
}
