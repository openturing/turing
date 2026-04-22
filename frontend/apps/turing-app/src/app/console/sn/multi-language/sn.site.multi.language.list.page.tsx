import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate.tsx";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteLocaleDraggableList } from "@/components/sn/locales/sn.site.locale.draggable.list";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model.ts";
import { TurSNSiteLocaleService } from "@/services/sn/sn.site.locale.service";
import { TurFeaturesService } from "@/services/system/features.service";
import { IconLanguage } from "@tabler/icons-react";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turSNSiteLocaleService = new TurSNSiteLocaleService();
const turFeaturesService = new TurFeaturesService();
export default function SNSiteMultiLanguageListPage() {
    const { id } = useParams() as { id: string };
    const [data, setData] = React.useState<TurSNSiteLocale[]>();
    const [error, setError] = React.useState<string | null>(null);
    const [storageEnabled, setStorageEnabled] = React.useState(false);
    const { t } = useTranslation();
    useSubPageBreadcrumb(t("sn.multiLanguage.title"));

    React.useEffect(() => {
        turSNSiteLocaleService.query(id).then(setData).catch(() => setError("Connection error or timeout while fetching Multi Language data."));
        turFeaturesService.getFeatures().then((features) => setStorageEnabled(features.storageEnabled)).catch(() => setStorageEnabled(false));
    }, [id]);

    return (
        <LoadProvider checkIsNotUndefined={data} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/locale`}>
            <SubPageHeader icon={IconLanguage} name={t("sn.multiLanguage.title")} feature={t("sn.multiLanguage.feature")}
                description={t("sn.multiLanguage.description")}>
                <SubPageHeader.Action label={t("sn.multiLanguage.newLanguage")} href={`${ROUTES.SN_INSTANCE}/${id}/locale/new`} />
            </SubPageHeader>
            {data && data.length > 0 ? (
                <SNSiteLocaleDraggableList siteId={id} tableData={data}
                    storageEnabled={storageEnabled}
                    setTableData={setData as React.Dispatch<React.SetStateAction<TurSNSiteLocale[]>>} />
            ) : (
                <BlankSlate
                    icon={IconLanguage}
                    title={t("sn.multiLanguage.blankTitle")}
                    description={t("sn.multiLanguage.blankDescription")}
                    buttonText={t("sn.multiLanguage.newLanguageBlank")}
                    urlNew={`${ROUTES.SN_INSTANCE}/${id}/locale/new`}
                />
            )}
        </LoadProvider>
    )
}
