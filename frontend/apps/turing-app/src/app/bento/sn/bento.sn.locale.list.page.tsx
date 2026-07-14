import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { BlankSlate } from "@/components/blank-slate";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteLocaleDraggableList } from "@/components/sn/locales/sn.site.locale.draggable.list";
import { GradientButtonLink } from "@viglet/viglet-design-system/router";
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model.ts";
import { TurSNSiteLocaleService } from "@/services/sn/sn.site.locale.service";
import { TurFeaturesService } from "@/services/system/features.service";
import { IconLanguage, IconPlus } from "@tabler/icons-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const service = new TurSNSiteLocaleService();
const featuresService = new TurFeaturesService();

/** Bento SN locale (multi-language) list — T576. Reuses the draggable list with
 *  the bento base route so row edit links stay inside the shell. */
export default function BentoSNLocaleListPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [data, setData] = React.useState<TurSNSiteLocale[]>();
  const [storageEnabled, setStorageEnabled] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const instanceRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}`;
  const localeRoute = `${instanceRoute}/locale`;

  React.useEffect(() => {
    service.query(id).then(setData).catch(() => setError(t("common.connectionError", { resource: t("sn.multiLanguage.title") })));
    featuresService.getFeatures().then((f) => setStorageEnabled(f.storageEnabled)).catch(() => setStorageEnabled(false));
  }, [id, t]);

  return (
    <LoadProvider checkIsNotUndefined={data} error={error} tryAgainUrl={localeRoute}>
      <BentoHero
        backTo={instanceRoute}
        backLabel={t("sn.title")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
            <IconLanguage size={24} />
          </span>
        }
        title={t("sn.multiLanguage.title")}
        subtitle={t("sn.multiLanguage.description")}
        trailing={
          <GradientButtonLink to={`${localeRoute}/new`} size="sm">
            <IconPlus className="size-4" />
            {t("sn.multiLanguage.newLanguage")}
          </GradientButtonLink>
        }
      />
      {data && data.length > 0 ? (
        <SNSiteLocaleDraggableList
          siteId={id}
          tableData={data}
          storageEnabled={storageEnabled}
          setTableData={setData as React.Dispatch<React.SetStateAction<TurSNSiteLocale[]>>}
          baseRoute={ROUTES.BENTO_SN_INSTANCE}
        />
      ) : (
        <BlankSlate
          icon={IconLanguage}
          title={t("sn.multiLanguage.blankTitle")}
          description={t("sn.multiLanguage.blankDescription")}
          buttonText={t("sn.multiLanguage.newLanguageBlank")}
          urlNew={`${localeRoute}/new`}
        />
      )}
    </LoadProvider>
  );
}
