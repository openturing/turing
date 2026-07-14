import { ROUTES } from "@/app/routes.const";
import {
  useApplySynonyms,
  useImportAlgoliaSynonyms,
  useMineSynonyms,
  useSynonyms,
  useSynonymSupport,
} from "@/api/queries/sn-synonym.queries";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import type { TurSNSynonym } from "@/models/sn/sn-site-synonym.model";
import { IconArrowsShuffle, IconDownload, IconRocket, IconSparkles } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

/** Human summary of a rule for the tile description. */
function summarize(item: TurSNSynonym): string {
  const terms = item.terms.join(", ");
  return item.input ? `${item.input} → ${terms}` : terms;
}

/** Bento SN synonyms list — T666. Manage rules + push to engine + import from Algolia. */
export default function BentoSNSynonymListPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}`;
  const sectionRoute = `${instanceRoute}/synonym`;

  const { data: items, isError } = useSynonyms(id);
  const { data: support } = useSynonymSupport(id);
  const applyMutation = useApplySynonyms(id);
  const importMutation = useImportAlgoliaSynonyms(id);
  const mineMutation = useMineSynonyms(id);

  const [showImport, setShowImport] = useState(false);
  const [appId, setAppId] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [index, setIndex] = useState("");
  const [importLocale, setImportLocale] = useState("en-US");

  const error = isError
    ? t("common.connectionError", { resource: t("sn.synonym.title") })
    : null;

  async function onApply() {
    try {
      const results = await applyMutation.mutateAsync(undefined);
      const applied = Object.values(results).reduce((sum, r) => sum + r.applied, 0);
      toast.success(t("sn.synonym.applied", { count: applied }));
    } catch {
      toast.error(t("sn.synonym.applyFailed"));
    }
  }

  async function onSuggest() {
    try {
      const proposals = await mineMutation.mutateAsync(importLocale);
      if (proposals.length > 0) {
        toast.success(t("sn.synonym.suggested", { count: proposals.length }));
      } else {
        toast.info(t("sn.synonym.noSuggestions"));
      }
    } catch {
      toast.error(t("sn.synonym.suggestFailed"));
    }
  }

  async function onImport() {
    try {
      const imported = await importMutation.mutateAsync({
        appId,
        apiKey,
        index,
        language: importLocale,
      });
      toast.success(t("sn.synonym.imported", { count: imported.length }));
      setShowImport(false);
      setAppId("");
      setApiKey("");
      setIndex("");
    } catch {
      toast.error(t("sn.synonym.importFailed"));
    }
  }

  const headerAction = (
    <div className="flex flex-col items-end gap-2">
      <div className="flex flex-wrap items-center justify-end gap-2">
        {support && (
          <span className="rounded-full border border-border/60 bg-card/60 px-3 py-1 text-xs text-muted-foreground">
            {support.supported
              ? t("sn.synonym.engineSupported", { engine: support.engine })
              : t("sn.synonym.engineUnsupported", { engine: support.engine })}
          </span>
        )}
        <GradientButton
          type="button"
          onClick={onSuggest}
          disabled={mineMutation.isPending}
          className="gap-1.5"
        >
          <IconSparkles size={16} />
          {t("sn.synonym.suggest")}
        </GradientButton>
        <GradientButton
          type="button"
          onClick={() => setShowImport((v) => !v)}
          className="gap-1.5"
        >
          <IconDownload size={16} />
          {t("sn.synonym.importFromAlgolia")}
        </GradientButton>
        <GradientButton
          type="button"
          onClick={onApply}
          disabled={applyMutation.isPending || !support?.supported}
          className="gap-1.5"
        >
          <IconRocket size={16} />
          {t("sn.synonym.pushToEngine")}
        </GradientButton>
      </div>
      {showImport && (
        <div className="bento-tile bento-glass flex w-full max-w-md flex-col gap-2 rounded-2xl p-4 text-left">
          <p className="text-sm font-medium">{t("sn.synonym.importFromAlgolia")}</p>
          <Input placeholder={t("sn.synonym.algoliaAppId")} value={appId} onChange={(e) => setAppId(e.target.value)} />
          <Input placeholder={t("sn.synonym.algoliaApiKey")} type="password" value={apiKey} onChange={(e) => setApiKey(e.target.value)} />
          <Input placeholder={t("sn.synonym.algoliaIndex")} value={index} onChange={(e) => setIndex(e.target.value)} />
          <Input placeholder={t("sn.synonym.locale")} value={importLocale} onChange={(e) => setImportLocale(e.target.value)} />
          <GradientButton
            type="button"
            onClick={onImport}
            disabled={importMutation.isPending || !appId || !apiKey || !index}
          >
            {t("sn.synonym.runImport")}
          </GradientButton>
        </div>
      )}
    </div>
  );

  return (
    <BentoListPage
      items={items}
      error={error}
      tryAgainUrl={sectionRoute}
      backTo={instanceRoute}
      backLabel={t("sn.title")}
      heroIcon={IconArrowsShuffle}
      tone="emerald"
      title={t("sn.synonym.title")}
      subtitle={t("sn.synonym.description")}
      headerAction={headerAction}
      newRoute={`${sectionRoute}/new`}
      newLabel={t("sn.synonym.newSynonym")}
      itemKey={(item) => item.id ?? ""}
      emptyTitle={t("sn.synonym.blankTitle")}
      emptyDescription={t("sn.synonym.blankDescription")}
      renderTile={(item, emphasis) => (
        <BentoEntityTile
          to={`${sectionRoute}/${item.id}`}
          emphasis={emphasis}
          defaultIcon={IconArrowsShuffle}
          tone="emerald"
          title={item.name || t(`sn.synonym.type.${item.type}`)}
          description={summarize(item)}
        />
      )}
    />
  );
}
