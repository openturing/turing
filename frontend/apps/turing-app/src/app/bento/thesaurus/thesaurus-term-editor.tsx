import { useUpdateTerm } from "@/api/queries/thesaurus.queries";
import { GradientButton } from "@/components/ui/gradient-button";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { Input } from "@/components/ui/input";
import type {
  TurThesaurusRelationType,
  TurThesaurusTerm,
  TurThesaurusTermRelation,
  TurThesaurusTermVariation,
} from "@/models/kb/thesaurus.model";
import { IconPlus, IconX } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useState } from "react";
import { useTranslation } from "react-i18next";

const RELATION_TYPES: TurThesaurusRelationType[] = ["RELATED", "USE", "USED_FOR", "CUSTOM"];

interface Props {
  microId: string;
  term: TurThesaurusTerm;
  /** All terms in the microthesaurus, for the parent + relation-target pickers. */
  allTerms: TurThesaurusTerm[];
  onClose: () => void;
}

/**
 * Block AQ (§XL) / T679 — inline editor for a thesaurus term: rename, enable,
 * scope note, reparent (broader), recognition variations (surface form +
 * case/accent flags), and non-hierarchical relations (type + target term).
 */
export function ThesaurusTermEditor({ microId, term, allTerms, onClose }: Props) {
  const { t } = useTranslation();
  const updateTerm = useUpdateTerm(microId);

  const [label, setLabel] = useState(term.label);
  const [enabled, setEnabled] = useState(term.enabled);
  const [scopeNote, setScopeNote] = useState(term.scopeNote ?? "");
  const [parentTermId, setParentTermId] = useState(term.parentTermId ?? "");
  const [variations, setVariations] = useState<TurThesaurusTermVariation[]>(
    term.variations ?? [],
  );
  const [relations, setRelations] = useState<TurThesaurusTermRelation[]>(
    term.relations ?? [],
  );

  // Candidate parents / targets: every other term (a term can't point at itself).
  const others = allTerms.filter((x) => x.id !== term.id);

  function addVariation() {
    setVariations((v) => [
      ...v,
      { surfaceForm: "", caseSensitive: false, accentSensitive: true },
    ]);
  }
  function updateVariation(i: number, patch: Partial<TurThesaurusTermVariation>) {
    setVariations((v) => v.map((item, idx) => (idx === i ? { ...item, ...patch } : item)));
  }
  function removeVariation(i: number) {
    setVariations((v) => v.filter((_, idx) => idx !== i));
  }

  function addRelation() {
    setRelations((r) => [...r, { type: "RELATED", targetTermId: "" }]);
  }
  function updateRelation(i: number, patch: Partial<TurThesaurusTermRelation>) {
    setRelations((r) => r.map((item, idx) => (idx === i ? { ...item, ...patch } : item)));
  }
  function removeRelation(i: number) {
    setRelations((r) => r.filter((_, idx) => idx !== i));
  }

  async function save() {
    if (!label.trim()) {
      toast.error(t("thesaurus.termLabelRequired"));
      return;
    }
    const cleanVariations = variations.filter((v) => v.surfaceForm.trim());
    const cleanRelations = relations.filter((r) => r.targetTermId);
    try {
      await updateTerm.mutateAsync({
        ...term,
        label: label.trim(),
        enabled,
        scopeNote: scopeNote.trim() || null,
        parentTermId: parentTermId || null,
        variations: cleanVariations,
        relations: cleanRelations,
      });
      toast.success(t("thesaurus.termSaved"));
      onClose();
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  const selectClass =
    "h-8 rounded-lg border border-border/60 bg-card/60 px-2 text-sm backdrop-blur";

  return (
    <div className="bento-glass mt-1 flex flex-col gap-4 rounded-xl border border-indigo-400/40 p-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex min-w-48 flex-1 flex-col gap-1">
          <label className="text-xs text-muted-foreground">{t("thesaurus.termLabel")}</label>
          <Input value={label} onChange={(e) => setLabel(e.target.value)} className="h-8" />
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground">{t("thesaurus.broaderParent")}</label>
          <select className={selectClass} value={parentTermId} onChange={(e) => setParentTermId(e.target.value)}>
            <option value="">{t("thesaurus.noParent")}</option>
            {others.map((x) => (
              <option key={x.id} value={x.id}>{x.label}</option>
            ))}
          </select>
        </div>
        <label className="flex items-center gap-2 pb-1">
          <GradientSwitch checked={enabled} onCheckedChange={setEnabled} />
          <span className="text-sm">{t("thesaurus.enabled")}</span>
        </label>
      </div>

      <div className="flex flex-col gap-1">
        <label className="text-xs text-muted-foreground">{t("thesaurus.scopeNote")}</label>
        <textarea
          value={scopeNote}
          onChange={(e) => setScopeNote(e.target.value)}
          rows={2}
          className="rounded-lg border border-border/60 bg-card/60 px-2 py-1 text-sm backdrop-blur"
        />
      </div>

      {/* Variations */}
      <div className="flex flex-col gap-2">
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium">{t("thesaurus.variations")}</span>
          <button type="button" className="inline-flex items-center gap-1 text-xs text-indigo-500 hover:underline" onClick={addVariation}>
            <IconPlus size={14} /> {t("thesaurus.addVariation")}
          </button>
        </div>
        {variations.map((v, i) => (
          <div key={i} className="flex flex-wrap items-center gap-2">
            <Input
              value={v.surfaceForm}
              onChange={(e) => updateVariation(i, { surfaceForm: e.target.value })}
              placeholder={t("thesaurus.surfaceForm")}
              className="h-8 min-w-40 flex-1"
            />
            <label className="flex items-center gap-1 text-xs text-muted-foreground">
              <input type="checkbox" checked={v.caseSensitive ?? false} onChange={(e) => updateVariation(i, { caseSensitive: e.target.checked })} />
              {t("thesaurus.caseSensitive")}
            </label>
            <label className="flex items-center gap-1 text-xs text-muted-foreground">
              <input type="checkbox" checked={v.accentSensitive ?? true} onChange={(e) => updateVariation(i, { accentSensitive: e.target.checked })} />
              {t("thesaurus.accentSensitive")}
            </label>
            <button type="button" aria-label={t("common.delete")} className="rounded p-1 text-muted-foreground hover:text-destructive" onClick={() => removeVariation(i)}>
              <IconX size={14} />
            </button>
          </div>
        ))}
        {variations.length === 0 && <span className="text-xs text-muted-foreground">{t("thesaurus.noVariations")}</span>}
      </div>

      {/* Relations */}
      <div className="flex flex-col gap-2">
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium">{t("thesaurus.relations")}</span>
          <button type="button" className="inline-flex items-center gap-1 text-xs text-indigo-500 hover:underline" onClick={addRelation}>
            <IconPlus size={14} /> {t("thesaurus.addRelation")}
          </button>
        </div>
        {relations.map((r, i) => (
          <div key={i} className="flex flex-wrap items-center gap-2">
            <select className={selectClass} value={r.type} onChange={(e) => updateRelation(i, { type: e.target.value as TurThesaurusRelationType })}>
              {RELATION_TYPES.map((type) => (
                <option key={type} value={type}>{t(`thesaurus.relation.${type}`, { defaultValue: type })}</option>
              ))}
            </select>
            <select className={`${selectClass} min-w-40 flex-1`} value={r.targetTermId} onChange={(e) => updateRelation(i, { targetTermId: e.target.value })}>
              <option value="">{t("thesaurus.selectTarget")}</option>
              {others.map((x) => (
                <option key={x.id} value={x.id}>{x.label}</option>
              ))}
            </select>
            <button type="button" aria-label={t("common.delete")} className="rounded p-1 text-muted-foreground hover:text-destructive" onClick={() => removeRelation(i)}>
              <IconX size={14} />
            </button>
          </div>
        ))}
        {relations.length === 0 && <span className="text-xs text-muted-foreground">{t("thesaurus.noRelations")}</span>}
      </div>

      <div className="flex items-center gap-2">
        <GradientButton type="button" className="h-8" onClick={save} disabled={updateTerm.isPending}>
          {t("forms.common.save", { defaultValue: "Save" })}
        </GradientButton>
        <button type="button" className="text-xs text-muted-foreground hover:text-foreground" onClick={onClose}>
          {t("common.cancel")}
        </button>
      </div>
    </div>
  );
}
