import {
  useDeselectForSite,
  useKnowledgeBases,
  useMicrothesauri,
  useSaveSiteConfig,
  useSelectForSite,
  useSetSiteSelectionEnabled,
  useSiteConfig,
  useSiteSelections,
} from "@/api/queries/thesaurus.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoFormSection, BentoHero } from "@/components/bento";
import { GradientButton } from "@/components/ui/gradient-button";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { Input } from "@/components/ui/input";
import type { TurSNSiteMicrothesaurusConfig } from "@/models/kb/thesaurus.model";
import {
  IconAdjustments,
  IconPlus,
  IconSitemap,
  IconTrash,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Block AQ (§XL) / T681 — per-SN-site Thesaurus selection + index config
 * (frontend for T670). An SN site opts in by selecting microthesauri from the
 * library and tuning the backing field. No selection = legacy indexing.
 */
export default function BentoSNThesaurusPage() {
  const { id: siteId } = useParams() as { id: string };
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_SN_INSTANCE}/${siteId}`;

  return (
    <>
      <BentoHero
        backTo={instanceRoute}
        backLabel={t("sn.title")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-violet-500 to-indigo-600 text-white shadow-md">
            <IconSitemap size={24} />
          </span>
        }
        title={t("thesaurus.perSiteTitle")}
        subtitle={t("thesaurus.perSiteDesc")}
      />
      <ConfigSection siteId={siteId} />
      <SelectionSection siteId={siteId} />
    </>
  );
}

function ConfigSection({ siteId }: { siteId: string }) {
  const { t } = useTranslation();
  const { data: config } = useSiteConfig(siteId);
  const saveConfig = useSaveSiteConfig(siteId);

  const [draft, setDraft] = useState<TurSNSiteMicrothesaurusConfig>({
    enabled: false,
    fieldName: "microthesaurus_terms",
    boost: 0.8,
    includeSynonyms: true,
    pathFacetEnabled: false,
    pathFieldName: "microthesaurus_path",
  });

  useEffect(() => {
    if (config) setDraft(config);
  }, [config]);

  async function save() {
    try {
      await saveConfig.mutateAsync(draft);
      toast.success(t("thesaurus.configSaved"));
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <BentoFormSection
      icon={IconAdjustments}
      tone="indigo"
      title={t("thesaurus.indexConfig")}
      description={t("thesaurus.indexConfigDesc")}
    >
      <div className="flex flex-col gap-4">
        <label className="flex items-center justify-between gap-3">
          <span>
            <span className="block text-sm font-medium">{t("thesaurus.enableExpansion")}</span>
            <span className="block text-xs text-muted-foreground">{t("thesaurus.enableExpansionHint")}</span>
          </span>
          <GradientSwitch
            checked={draft.enabled}
            onCheckedChange={(v) => setDraft((d) => ({ ...d, enabled: v }))}
          />
        </label>

        <div className="flex flex-wrap gap-4">
          <div className="flex w-64 flex-col gap-1">
            <label className="text-xs text-muted-foreground">{t("thesaurus.fieldName")}</label>
            <Input
              value={draft.fieldName}
              onChange={(e) => setDraft((d) => ({ ...d, fieldName: e.target.value }))}
              placeholder="microthesaurus_terms"
            />
          </div>
          <div className="flex w-32 flex-col gap-1">
            <label className="text-xs text-muted-foreground">{t("thesaurus.boost")}</label>
            <Input
              type="number"
              step="0.1"
              min="0"
              value={String(draft.boost)}
              onChange={(e) => setDraft((d) => ({ ...d, boost: Number(e.target.value) }))}
            />
          </div>
        </div>

        <label className="flex items-center justify-between gap-3">
          <span>
            <span className="block text-sm font-medium">{t("thesaurus.includeSynonyms")}</span>
            <span className="block text-xs text-muted-foreground">{t("thesaurus.includeSynonymsHint")}</span>
          </span>
          <GradientSwitch
            checked={draft.includeSynonyms}
            onCheckedChange={(v) => setDraft((d) => ({ ...d, includeSynonyms: v }))}
          />
        </label>

        <label className="flex items-center justify-between gap-3">
          <span>
            <span className="block text-sm font-medium">{t("thesaurus.pathFacet")}</span>
            <span className="block text-xs text-muted-foreground">{t("thesaurus.pathFacetHint")}</span>
          </span>
          <GradientSwitch
            checked={draft.pathFacetEnabled}
            onCheckedChange={(v) => setDraft((d) => ({ ...d, pathFacetEnabled: v }))}
          />
        </label>

        {draft.pathFacetEnabled && (
          <div className="flex w-64 flex-col gap-1">
            <label className="text-xs text-muted-foreground">{t("thesaurus.pathFieldName")}</label>
            <Input
              value={draft.pathFieldName}
              onChange={(e) => setDraft((d) => ({ ...d, pathFieldName: e.target.value }))}
              placeholder="microthesaurus_path"
            />
          </div>
        )}

        <GradientButton type="button" className="w-fit" onClick={save} disabled={saveConfig.isPending}>
          {t("forms.common.save", { defaultValue: "Save" })}
        </GradientButton>
      </div>
    </BentoFormSection>
  );
}

function SelectionSection({ siteId }: { siteId: string }) {
  const { t } = useTranslation();
  const { data: selections } = useSiteSelections(siteId);
  const { data: kbs } = useKnowledgeBases();
  const selectForSite = useSelectForSite(siteId);
  const setEnabled = useSetSiteSelectionEnabled(siteId);
  const deselect = useDeselectForSite(siteId);

  const [kbId, setKbId] = useState("");
  const { data: microthesauri } = useMicrothesauri(kbId || undefined);
  const [microId, setMicroId] = useState("");

  const selectedIds = useMemo(
    () => new Set((selections ?? []).map((s) => s.microthesaurusId)),
    [selections],
  );
  const available = (microthesauri ?? []).filter((m) => !selectedIds.has(m.id));

  async function add() {
    if (!microId) return;
    try {
      await selectForSite.mutateAsync(microId);
      setMicroId("");
      toast.success(t("thesaurus.selectionAdded"));
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  const selectClass =
    "h-9 rounded-lg border border-border/60 bg-card/60 px-2 text-sm backdrop-blur";

  return (
    <BentoFormSection
      icon={IconSitemap}
      tone="violet"
      title={t("thesaurus.selectedMicrothesauri")}
      description={t("thesaurus.selectedMicrothesauriDesc")}
    >
      <div className="flex flex-col gap-2">
        {(selections ?? []).map((sel) => (
          <div
            key={sel.id}
            className="bento-tile bento-glass flex items-center justify-between gap-3 rounded-2xl border border-border/60 px-4 py-3"
          >
            <span className="min-w-0">
              <span className="block truncate font-medium">{sel.name ?? sel.microthesaurusId}</span>
              <span className="block truncate text-xs text-muted-foreground">
                {[sel.language, sel.domain].filter(Boolean).join(" · ")}
              </span>
            </span>
            <div className="flex shrink-0 items-center gap-3">
              <GradientSwitch
                checked={sel.enabled}
                onCheckedChange={(v) => setEnabled.mutate({ selectionId: sel.id, enabled: v })}
              />
              <button
                type="button"
                aria-label={t("common.delete")}
                className="rounded-lg p-1.5 text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                onClick={() => deselect.mutate(sel.id)}
              >
                <IconTrash size={16} />
              </button>
            </div>
          </div>
        ))}
        {(selections ?? []).length === 0 && (
          <p className="text-sm text-muted-foreground">{t("thesaurus.noSelections")}</p>
        )}
      </div>

      <div className="mt-4 flex flex-wrap items-end gap-2 border-t border-border/50 pt-4">
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground">{t("thesaurus.knowledgeBase")}</label>
          <select className={selectClass} value={kbId} onChange={(e) => { setKbId(e.target.value); setMicroId(""); }}>
            <option value="">{t("thesaurus.selectLibrary")}</option>
            {(kbs ?? []).map((kb) => (
              <option key={kb.id} value={kb.id}>{kb.name}</option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground">{t("thesaurus.microthesaurus")}</label>
          <select className={selectClass} value={microId} onChange={(e) => setMicroId(e.target.value)} disabled={!kbId}>
            <option value="">{t("thesaurus.selectMicrothesaurus")}</option>
            {available.map((m) => (
              <option key={m.id} value={m.id}>{m.name} ({m.language})</option>
            ))}
          </select>
        </div>
        <GradientButton type="button" className="gap-1" onClick={add} disabled={!microId || selectForSite.isPending}>
          <IconPlus size={16} />
          {t("thesaurus.addSelection")}
        </GradientButton>
      </div>
    </BentoFormSection>
  );
}
