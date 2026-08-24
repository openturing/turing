import { useSnSiteLocales } from "@/api/queries/locale.queries";
import { ROUTES } from "@/app/routes.const";
import { useDeleteSynonym, useSaveSynonym, useSynonym } from "@/api/queries/sn-synonym.queries";
import { BentoFormHero, BentoFormSection, bentoChipClass } from "@/components/bento";
import { GradientButton } from "@/components/ui/gradient-button";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type {
  TurSNSynonym,
  TurSNSynonymType,
} from "@/models/sn/sn-site-synonym.model";
import { IconArrowsShuffle, IconTrash, IconX } from "@tabler/icons-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const SYNONYM_TYPES: TurSNSynonymType[] = [
  "REGULAR",
  "ONE_WAY",
  "ALTERNATIVE_CORRECTION_1",
  "ALTERNATIVE_CORRECTION_2",
  "PLACEHOLDER",
];

/** Bento SN synonym editor — T666. Per-type editor for one synonym rule. */
export default function BentoSNSynonymPage() {
  const { id, synonymId } = useParams() as { id: string; synonymId: string };
  const { t } = useTranslation();
  const navigate = useNavigate();
  const listRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/synonym`;
  const isNew = synonymId === "new";

  const { data: loaded } = useSynonym(id, synonymId);
  const saveMutation = useSaveSynonym(id);
  const deleteMutation = useDeleteSynonym(id);

  // T1018 — the site's locales from the shared cache, the same entry the SN
  // locale pages fill.
  const { data: locales = [] } = useSnSiteLocales(id);
  const [type, setType] = useState<TurSNSynonymType>("REGULAR");
  const [language, setLanguage] = useState("en-US");
  const [name, setName] = useState("");
  const [input, setInput] = useState("");
  const [terms, setTerms] = useState<string[]>([]);
  const [enabled, setEnabled] = useState(true);
  const [termDraft, setTermDraft] = useState("");
  const [dirty, setDirty] = useState(false);

  // T1017/T1018 — both seeds happen during render. The record fills the editable
  // fields when it arrives; on the `new` route there is no record, and the
  // language falls to the site's first locale instead of the "en-US" placeholder
  // — the same substitution the effect made, one committed render earlier.
  const [synced, setSynced] = useState<{ loaded?: typeof loaded; localeCount: number } | null>(null);
  if (loaded && synced?.loaded !== loaded) {
    setSynced({ loaded, localeCount: locales.length });
    setType(loaded.type);
    setLanguage(loaded.language);
    setName(loaded.name ?? "");
    setInput(loaded.input ?? "");
    setTerms(loaded.terms ?? []);
    setEnabled(loaded.enabled);
    setDirty(false);
  } else if (isNew && locales.length > 0 && synced?.localeCount !== locales.length) {
    setSynced({ localeCount: locales.length });
    setLanguage((prev) => (prev === "en-US" ? locales[0].language : prev));
  }

  const isDirectional = type !== "REGULAR";
  const inputLabel = useMemo(() => {
    switch (type) {
      case "ONE_WAY":
        return t("sn.synonym.inputOneWay");
      case "PLACEHOLDER":
        return t("sn.synonym.inputPlaceholder");
      case "ALTERNATIVE_CORRECTION_1":
      case "ALTERNATIVE_CORRECTION_2":
        return t("sn.synonym.inputCorrection");
      default:
        return t("sn.synonym.input");
    }
  }, [type, t]);

  const valid = isDirectional
    ? input.trim().length > 0 && terms.length >= 1
    : terms.length >= 2;

  function touch<T>(setter: (v: T) => void) {
    return (value: T) => {
      setter(value);
      setDirty(true);
    };
  }

  function addTerm() {
    const value = termDraft.trim();
    if (value && !terms.some((tm) => tm.toLowerCase() === value.toLowerCase())) {
      setTerms([...terms, value]);
      setDirty(true);
    }
    setTermDraft("");
  }

  function removeTerm(term: string) {
    setTerms(terms.filter((tm) => tm !== term));
    setDirty(true);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid) return;
    const payload: TurSNSynonym = {
      id: isNew ? undefined : synonymId,
      name: name || null,
      type,
      language,
      input: isDirectional ? input.trim() : null,
      terms,
      enabled,
    };
    try {
      await saveMutation.mutateAsync(payload);
      toast.success(t("sn.synonym.saved"));
      navigate(listRoute);
    } catch {
      toast.error(t("sn.synonym.saveFailed"));
    }
  }

  async function onDelete() {
    if (isNew) return;
    try {
      await deleteMutation.mutateAsync(synonymId);
      toast.success(t("sn.synonym.deleted"));
      navigate(listRoute);
    } catch {
      toast.error(t("sn.synonym.deleteFailed"));
    }
  }

  const heroTitle = isNew ? t("sn.synonym.newSynonym") : name || t(`sn.synonym.type.${type}`);

  return (
    <form onSubmit={onSubmit}>
      <BentoFormHero
        backTo={listRoute}
        backLabel={t("sn.synonym.title")}
        leading={
          <span className={`grid h-12 w-12 place-items-center rounded-2xl ${bentoChipClass("emerald")} text-white shadow-sm`}>
            <IconArrowsShuffle size={24} />
          </span>
        }
        title={heroTitle}
        subtitle={t("sn.synonym.description")}
        trailing={
          !isNew && (
            <GradientButton type="button" variant="outline" onClick={onDelete} className="gap-1.5">
              <IconTrash size={16} />
              {t("common.delete", { defaultValue: "Delete" })}
            </GradientButton>
          )
        }
        onCancel={() => navigate(listRoute)}
        loading={saveMutation.isPending}
        dirty={dirty}
        titleMissing={!valid}
        stickyTitle={heroTitle}
      />

      <div className="bento-grid mx-auto grid w-full max-w-4xl gap-4 px-4 pb-32 md:px-8">
        <BentoFormSection
          icon={IconArrowsShuffle}
          tone="emerald"
          title={t("sn.synonym.ruleTitle")}
          description={t("sn.synonym.ruleDescription")}
        >
          <div className="grid gap-4">
            <div className="grid gap-2">
              <Label>{t("sn.synonym.typeLabel")}</Label>
              <Select value={type} onValueChange={(v) => touch(setType)(v as TurSNSynonymType)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {SYNONYM_TYPES.map((tp) => (
                    <SelectItem key={tp} value={tp}>{t(`sn.synonym.type.${tp}`)}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label>{t("sn.synonym.localeLabel")}</Label>
              <Select value={language} onValueChange={touch(setLanguage)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {locales.map((l) => (
                    <SelectItem key={l.id} value={l.language}>{l.language}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label>{t("sn.synonym.nameLabel")}</Label>
              <Input value={name} onChange={(e) => touch(setName)(e.target.value)} placeholder={t("sn.synonym.namePlaceholder")} />
            </div>

            {isDirectional && (
              <div className="grid gap-2">
                <Label>{inputLabel}</Label>
                <Input value={input} onChange={(e) => touch(setInput)(e.target.value)} />
              </div>
            )}

            <div className="grid gap-2">
              <Label>{isDirectional ? t("sn.synonym.termsExpansions") : t("sn.synonym.termsEquivalent")}</Label>
              <div className="flex flex-wrap gap-2">
                {terms.map((term) => (
                  <span key={term} className="inline-flex items-center gap-1 rounded-full border border-border/60 bg-card/60 px-3 py-1 text-sm">
                    {term}
                    <button type="button" aria-label={t("sn.synonym.removeTerm", { term })} onClick={() => removeTerm(term)} className="text-muted-foreground hover:text-foreground">
                      <IconX size={14} />
                    </button>
                  </span>
                ))}
              </div>
              <div className="flex gap-2">
                <Input
                  value={termDraft}
                  onChange={(e) => setTermDraft(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      addTerm();
                    }
                  }}
                  placeholder={t("sn.synonym.addTermPlaceholder")}
                />
                <GradientButton type="button" variant="outline" onClick={addTerm}>
                  {t("sn.synonym.addTerm")}
                </GradientButton>
              </div>
            </div>

            <div className="flex items-center justify-between">
              <Label>{t("sn.synonym.enabledLabel")}</Label>
              <GradientSwitch checked={enabled} onCheckedChange={touch(setEnabled)} />
            </div>
          </div>
        </BentoFormSection>
      </div>
    </form>
  );
}
