import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate.tsx";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteMultiLanguageDataTable } from "@/components/sn/locales/sn.site.locale.data.table.tsx";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model.ts";
import { TurSNSiteLocaleService } from "@/services/sn/sn.site.locale.service";
import { IconLanguage } from "@tabler/icons-react";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import * as React from "react";
import { useParams } from "react-router-dom";

const turSNSiteLocaleService = new TurSNSiteLocaleService();
export default function SNSiteMultiLanguageListPage() {
    const { id } = useParams() as { id: string };
    const [data, setData] = React.useState<TurSNSiteLocale[]>();
    const [error, setError] = React.useState<string | null>(null);
    useSubPageBreadcrumb("Multi Languages");

    React.useEffect(() => {
        turSNSiteLocaleService.query(id).then(setData).catch(() => setError("Connection error or timeout while fetching Multi Language data."));
    }, [id]);

    return (
        <LoadProvider checkIsNotUndefined={data} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/locale`}>
            <SubPageHeader icon={IconLanguage} name="Multi Language" feature="Multi Language"
                description="Define Multi Languages.">
                <SubPageHeader.Action label="New Language" href={`${ROUTES.SN_INSTANCE}/${id}/locale/new`} />
            </SubPageHeader>
            {data && data.length > 0 ? (
                <SNSiteMultiLanguageDataTable data={data} />
            ) : (
                < BlankSlate
                    icon={IconLanguage}
                    title="You don't seem to have any language."
                    description="Create a new language to semantic navigation search works."
                    buttonText="New language"
                    urlNew={`${ROUTES.SN_INSTANCE}/${id}/locale/new`}
                />
            )}
        </LoadProvider>
    )
}
