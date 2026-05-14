import * as React from "react"

import { ROUTES } from "@/app/routes.const"
import { LoadProvider } from "@/components/loading-provider"
import { SNSiteFieldGridList } from "@/components/sn/fields/field.grid.list"
import { SubPageHeader } from "@/components/sub.page.header"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import type { TurSNStatusFields } from "@/models/sn/sn-field-status.model"
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model.ts"
import { TurSNFieldService } from "@/services/sn/sn.field.service"
import { IconAlignBoxCenterStretch } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router-dom"


const turSNFieldService = new TurSNFieldService();
export default function SNSiteFieldListPage() {
    const { id } = useParams() as { id: string };
    const [data, setSnField] = React.useState<TurSNSiteField[]>();
    const [statusFields, setStatusFields] = React.useState<TurSNStatusFields>();
    const [error, setError] = React.useState<string | null>(null);
    const { t } = useTranslation();
    useSubPageBreadcrumb(t("sn.fields.title"));

    const refreshStatus = React.useCallback(async () => {
        try {
            const result = await turSNFieldService.getStatusFields(id);
            setStatusFields(result);
        } catch {
            setError("Connection error or timeout while fetching SN field data.");
        }
    }, [id]);

    React.useEffect(() => {
        turSNFieldService.query(id).then(setSnField).catch(() => setError("Connection error or timeout while fetching SN field data."));
        void refreshStatus();
    }, [id, refreshStatus]);
    return (
        <LoadProvider checkIsNotUndefined={data && statusFields} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/field`}>
            {data && statusFields && (
                <>
                    <SubPageHeader
                        icon={IconAlignBoxCenterStretch}
                        name={t("sn.fields.feature")}
                        feature={t("sn.fields.feature")}
                        description={t("sn.fields.description")}>
                        <SubPageHeader.Action label={t("sn.fields.newField")} href={`${ROUTES.SN_INSTANCE}/${id}/field/new`} />
                    </SubPageHeader>
                    <SNSiteFieldGridList id={id} statusFields={statusFields} data={data} setSnField={setSnField as React.Dispatch<React.SetStateAction<TurSNSiteField[]>>} onRefreshStatus={refreshStatus} />
                </>
            )}
        </LoadProvider>
    )
}
