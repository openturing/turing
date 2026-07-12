import {
  useCreateFromDraft,
  useCreateKnowledgeBase,
  useCreateMicrothesaurus,
  useGenerateThesaurusDraft,
  useImportSeed,
  useThesaurusSeeds,
  useUpdateKnowledgeBase,
  useUploadAuthorityFile,
} from "@/api/queries/thesaurus.queries";
import { ROUTES } from "@/app/routes.const";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import type { TurMicrothesaurus, TurThesaurusDraft } from "@/models/kb/thesaurus.model";
import {
  IconDownload,
  IconLibrary,
  IconPencil,
  IconPlus,
  IconSitemap,
  IconSparkles,
  IconUpload,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useRef, useState, type ComponentType, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

/**
 * Block AQ — the three ways to create a microthesaurus, unified. Before this,
 * "add manually", "generate with AI" and "import" were three separate stacked
 * cards that read as unrelated features. They are one action ("create a
 * microthesaurus") with a method choice, so this presents a single method
 * selector and only the fields for the chosen method.
 */
type CreateMethod = "MANUAL" | "AI" | "IMPORT";

const METHODS: ReadonlyArray<{
  id: CreateMethod;
  icon: ComponentType<{ size?: number; className?: string }>;
  labelKey: string;
  descKey: string;
}> = [
  { id: "MANUAL", icon: IconPencil, labelKey: "thesaurus.method.manual", descKey: "thesaurus.method.manualDesc" },
  { id: "AI", icon: IconSparkles, labelKey: "thesaurus.method.ai", descKey: "thesaurus.method.aiDesc" },
  { id: "IMPORT", icon: IconLibrary, labelKey: "thesaurus.method.import", descKey: "thesaurus.method.importDesc" },
];

interface MicrothesaurusCreatorProps {
  kbId: string;
  /** Fired after any method successfully creates the microthesaurus. */
  onCreated: (created: TurMicrothesaurus) => void;
  /** Buttons rendered on the left of the footer (e.g. Cancel, or Back/Skip). */
  secondaryActions?: ReactNode;
}

/**
 * The method selector + per-method fields + method-specific primary action.
 * Chrome-agnostic so it can sit inside either the "add microthesaurus" dialog
 * or step 2 of the new-thesaurus wizard. Requires a persisted `kbId` — every
 * backend path (manual create, AI generate/create-from-draft, seed import, XML
 * upload) is scoped to the knowledge base.
 */
export function MicrothesaurusCreator({ kbId, onCreated, secondaryActions }: Readonly<MicrothesaurusCreatorProps>) {
  const { t } = useTranslation();
  const [method, setMethod] = useState<CreateMethod>("MANUAL");

  // ── Manual ──
  const createManual = useCreateMicrothesaurus(kbId);
  const [name, setName] = useState("");
  const [language, setLanguage] = useState("pt");
  const [domain, setDomain] = useState("GENERAL");

  // ── AI ──
  const generate = useGenerateThesaurusDraft(kbId);
  const createFromDraft = useCreateFromDraft(kbId);
  const [aiDomain, setAiDomain] = useState("");
  const [aiLanguage, setAiLanguage] = useState("pt");
  const [guidance, setGuidance] = useState("");
  const [draft, setDraft] = useState<TurThesaurusDraft | null>(null);

  // ── Import ──
  const { data: seeds } = useThesaurusSeeds();
  const importSeed = useImportSeed(kbId);
  const upload = useUploadAuthorityFile(kbId);
  const fileRef = useRef<HTMLInputElement>(null);

  async function addManual() {
    if (!name.trim()) {
      toast.error(t("thesaurus.nameRequired"));
      return;
    }
    try {
      const created = await createManual.mutateAsync({
        id: "",
        name: name.trim(),
        language: language.trim() || "pt",
        domain: domain.trim() || "GENERAL",
      } as TurMicrothesaurus);
      toast.success(t("thesaurus.microthesaurusAdded"));
      onCreated(created);
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  async function runGenerate() {
    if (!aiDomain.trim()) {
      toast.error(t("thesaurus.generateDomainRequired"));
      return;
    }
    try {
      const result = await generate.mutateAsync({
        domain: aiDomain.trim(),
        language: aiLanguage.trim() || "pt",
        guidance: guidance.trim() || undefined,
      });
      setDraft(result);
    } catch (err) {
      console.error(err);
      toast.error(t("thesaurus.generateFailed"));
    }
  }

  async function acceptDraft() {
    if (!draft) return;
    try {
      const created = await createFromDraft.mutateAsync(draft);
      toast.success(t("thesaurus.seedImported", { name: created.name }));
      setDraft(null);
      onCreated(created);
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  async function doImportSeed(seedId: string, seedName: string) {
    try {
      const created = await importSeed.mutateAsync(seedId);
      toast.success(t("thesaurus.seedImported", { name: seedName }));
      onCreated(created);
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
      onCreated(created);
    } catch (err) {
      console.error(err);
      toast.error(t("thesaurus.uploadFailed"));
    } finally {
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  return (
    <div className="flex flex-col gap-5">
      {/* Method selector — three ways to do one thing, made explicit. */}
      <div className="grid grid-cols-3 gap-2">
        {METHODS.map((m) => {
          const active = m.id === method;
          const Icon = m.icon;
          return (
            <button
              key={m.id}
              type="button"
              onClick={() => setMethod(m.id)}
              aria-pressed={active}
              className={`bento-tile-clickable flex flex-col items-center gap-1.5 rounded-2xl border p-3 text-center transition-colors ${
                active
                  ? "border-violet-500/60 bg-violet-500/10 text-violet-600 dark:text-violet-300"
                  : "border-border/60 bg-card/40 text-muted-foreground hover:bg-card/70"
              }`}
            >
              <Icon size={20} className={active ? "text-violet-500" : ""} />
              <span className="text-sm font-medium text-foreground">{t(m.labelKey)}</span>
              <span className="text-[11px] leading-tight text-muted-foreground">{t(m.descKey)}</span>
            </button>
          );
        })}
      </div>

      {method === "MANUAL" && (
        <div className="flex flex-col gap-3">
          <div className="flex flex-col gap-1">
            <label className="text-xs text-muted-foreground">{t("thesaurus.microthesaurusName")}</label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder={t("thesaurus.microthesaurusName")} autoFocus />
          </div>
          <div className="flex gap-2">
            <div className="flex w-28 flex-col gap-1">
              <label className="text-xs text-muted-foreground">{t("thesaurus.language")}</label>
              <Input value={language} onChange={(e) => setLanguage(e.target.value)} placeholder="pt" />
            </div>
            <div className="flex flex-1 flex-col gap-1">
              <label className="text-xs text-muted-foreground">{t("thesaurus.domain")}</label>
              <Input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="GENERAL" />
            </div>
          </div>
        </div>
      )}

      {method === "AI" && (
        <div className="flex flex-col gap-3">
          <p className="text-xs text-muted-foreground">{t("thesaurus.generateDesc")}</p>
          <div className="flex gap-2">
            <div className="flex flex-1 flex-col gap-1">
              <label className="text-xs text-muted-foreground">{t("thesaurus.domain")}</label>
              <Input value={aiDomain} onChange={(e) => setAiDomain(e.target.value)} placeholder="EDUCATION" />
            </div>
            <div className="flex w-28 flex-col gap-1">
              <label className="text-xs text-muted-foreground">{t("thesaurus.language")}</label>
              <Input value={aiLanguage} onChange={(e) => setAiLanguage(e.target.value)} placeholder="pt" />
            </div>
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-xs text-muted-foreground">{t("thesaurus.generateGuidance")}</label>
            <Input
              value={guidance}
              onChange={(e) => setGuidance(e.target.value)}
              placeholder={t("thesaurus.generateGuidancePlaceholder")}
            />
          </div>
          {draft && (
            <div className="bento-fade-in rounded-2xl border border-border/60 bg-card/40 p-3">
              <div className="mb-2 min-w-0">
                <span className="block truncate font-medium">{draft.name}</span>
                <span className="block truncate text-xs text-muted-foreground">
                  {draft.language} · {draft.domain} · {t("thesaurus.termCount", { count: draft.terms.length })}
                </span>
              </div>
              <DraftPreview draft={draft} />
            </div>
          )}
        </div>
      )}

      {method === "IMPORT" && (
        <div className="flex flex-col gap-2">
          <p className="text-xs text-muted-foreground">{t("thesaurus.libraryDesc")}</p>
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
          {(seeds ?? []).length === 0 && <p className="text-sm text-muted-foreground">{t("thesaurus.noSeeds")}</p>}
          <input ref={fileRef} type="file" accept=".xml,text/xml,application/xml" className="hidden" onChange={onFile} />
        </div>
      )}

      <div className="flex items-center justify-between gap-2 border-t border-border/50 pt-4">
        <div className="flex items-center gap-2">{secondaryActions}</div>
        <div className="flex items-center gap-2">
          {method === "MANUAL" && (
            <GradientButton type="button" className="gap-1" loading={createManual.isPending} onClick={addManual}>
              <IconPlus size={16} />
              {t("thesaurus.addMicrothesaurus")}
            </GradientButton>
          )}
          {method === "AI" && !draft && (
            <GradientButton type="button" className="gap-1" disabled={generate.isPending} onClick={runGenerate}>
              <IconSparkles size={16} />
              {generate.isPending ? t("thesaurus.generating") : t("thesaurus.generate")}
            </GradientButton>
          )}
          {method === "AI" && draft && (
            <>
              <Button type="button" variant="ghost" onClick={() => setDraft(null)}>
                {t("thesaurus.discard")}
              </Button>
              <GradientButton type="button" className="gap-1" loading={createFromDraft.isPending} onClick={acceptDraft}>
                <IconPlus size={16} />
                {t("thesaurus.createFromDraft")}
              </GradientButton>
            </>
          )}
          {method === "IMPORT" && (
            <GradientButton type="button" className="gap-1" loading={upload.isPending} onClick={() => fileRef.current?.click()}>
              <IconUpload size={16} />
              {t("thesaurus.uploadXml")}
            </GradientButton>
          )}
        </div>
      </div>
    </div>
  );
}

/** Read-only indented preview of a generated draft, built from the broader spine. */
function DraftPreview({ draft }: Readonly<{ draft: TurThesaurusDraft }>) {
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
  const ordered = [...draft.terms].sort((a, b) => depthOf(a.id) - depthOf(b.id));
  return (
    <ul className="flex max-h-60 flex-col gap-1 overflow-y-auto text-sm">
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

interface ThesaurusMicroWizardDialogProps {
  kbId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated?: (created: TurMicrothesaurus) => void;
}

/** "New microthesaurus" modal launched from an existing thesaurus's detail page. */
export function ThesaurusMicroWizardDialog({
  kbId,
  open,
  onOpenChange,
  onCreated,
}: Readonly<ThesaurusMicroWizardDialogProps>) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t("thesaurus.newMicrothesaurus")}</DialogTitle>
          <DialogDescription>{t("thesaurus.chooseMethod")}</DialogDescription>
        </DialogHeader>
        <MicrothesaurusCreator
          kbId={kbId}
          onCreated={(created) => {
            onCreated?.(created);
            onOpenChange(false);
          }}
          secondaryActions={
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              {t("common.cancel", { defaultValue: "Cancel" })}
            </Button>
          }
        />
      </DialogContent>
    </Dialog>
  );
}

/**
 * The guided "new thesaurus" wizard. Step 1 collects the library's name +
 * description and persists it (so the microthesaurus paths have a real `kbId`);
 * step 2 reuses {@link MicrothesaurusCreator} to add the first microthesaurus.
 * Replaces the old bare name+description detail screen, which read as an empty
 * form with no clear next step.
 */
export function ThesaurusNewWizard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const create = useCreateKnowledgeBase();
  const update = useUpdateKnowledgeBase();

  const [step, setStep] = useState<1 | 2>(1);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [kbId, setKbId] = useState<string | null>(null);

  function close() {
    navigate(ROUTES.BENTO_THESAURUS);
  }

  function goToKb(id: string) {
    navigate(`${ROUTES.BENTO_THESAURUS}/${id}`);
  }

  async function next() {
    if (!name.trim()) {
      toast.error(t("thesaurus.nameRequired"));
      return;
    }
    try {
      if (kbId) {
        // Returning from step 2 with edits — update the already-created library.
        await update.mutateAsync({ id: kbId, name: name.trim(), description });
      } else {
        const created = await create.mutateAsync({ id: "", name: name.trim(), description });
        setKbId(created.id);
      }
      setStep(2);
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <Dialog open onOpenChange={(o) => { if (!o) close(); }}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t("thesaurus.newKnowledgeBase")}</DialogTitle>
          <DialogDescription>
            {step === 1 ? t("thesaurus.wizardStepInfo") : t("thesaurus.wizardStepMicro")}
          </DialogDescription>
        </DialogHeader>

        {step === 1 && (
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1">
              <label className="text-xs text-muted-foreground">{t("forms.common.title")}</label>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder={t("thesaurus.title")} autoFocus />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs text-muted-foreground">{t("forms.common.description")}</label>
              <Textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder={t("forms.common.description")}
                rows={3}
              />
            </div>
            <div className="flex items-center justify-between gap-2 border-t border-border/50 pt-4">
              <Button type="button" variant="ghost" onClick={close}>
                {t("common.cancel", { defaultValue: "Cancel" })}
              </Button>
              <GradientButton
                type="button"
                className="gap-1"
                loading={create.isPending || update.isPending}
                onClick={next}
              >
                {t("common.next", { defaultValue: "Next" })}
              </GradientButton>
            </div>
          </div>
        )}

        {step === 2 && kbId && (
          <MicrothesaurusCreator
            kbId={kbId}
            onCreated={() => goToKb(kbId)}
            secondaryActions={
              <>
                <Button type="button" variant="ghost" onClick={() => setStep(1)}>
                  {t("common.back", { defaultValue: "Back" })}
                </Button>
                <Button type="button" variant="outline" onClick={() => goToKb(kbId)}>
                  {t("thesaurus.skip", { defaultValue: "Skip for now" })}
                </Button>
              </>
            }
          />
        )}
      </DialogContent>
    </Dialog>
  );
}
