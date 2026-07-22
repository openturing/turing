import * as React from "react"

import { ROUTES } from "@/app/routes.const"
import { LoadProvider } from "@/components/loading-provider"
import { SNSiteFieldGridList } from "@/components/sn/fields/field.grid.list"
import { SubPageHeader } from "@/components/sub.page.header"
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import type { TurProfile } from "@/models/profile/profile.model"
import type { TurSNStatusFields } from "@/models/sn/sn-field-status.model"
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model.ts"
import { TurSNFieldService } from "@/services/sn/sn.field.service"
import { TurSNFacetProfileService } from "@/services/sn/sn.facet.profile.service"
import { IconAlignBoxCenterStretch } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router-dom"


const turSNFieldService = new TurSNFieldService();
const facetProfileService = new TurSNFacetProfileService();
export default function SNSiteFieldListPage() {
    const { id } = useParams() as { id: string };
    const [data, setSnField] = React.useState<TurSNSiteField[]>();
    const [statusFields, setStatusFields] = React.useState<TurSNStatusFields>();
    const [profiles, setProfiles] = React.useState<TurProfile[]>();
    const [selectedProfileId, setSelectedProfileId] = React.useState<string>();
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

    React.useEffect(() => {
        facetProfileService
            .query(id)
            .then((list) => {
                setProfiles(list);
                setSelectedProfileId((current) =>
                    current && list.some((profile) => profile.id === current)
                        ? current
                        : (list.find((profile) => profile.default)?.id ?? list[0]?.id),
                );
            })
            .catch(() => setError(t("sn.fields.profile.loadError")));
    }, [id, t]);

    const selectedProfile = profiles?.find((profile) => profile.id === selectedProfileId);
    // Default until a profile is resolved, so we never briefly show the per-profile table for the default.
    const isDefaultProfile = selectedProfile?.default ?? true;

    return (
        <LoadProvider checkIsNotUndefined={data && statusFields && profiles} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/field`}>
            {data && statusFields && profiles && (
                <>
                    <SubPageHeader
                        icon={IconAlignBoxCenterStretch}
                        name={t("sn.fields.feature")}
                        feature={t("sn.fields.feature")}
                        description={t("sn.fields.description")}>
                        <SubPageHeader.Action label={t("sn.fields.profile.manage")} href={ROUTES.PROFILE_INSTANCE} />
                        <SubPageHeader.Action label={t("sn.fields.newField")} href={`${ROUTES.SN_INSTANCE}/${id}/field/new`} />
                    </SubPageHeader>

                    <div className="px-6 flex flex-col gap-1 mb-2">
                        <span className="text-sm text-muted-foreground">{t("sn.fields.profile.label")}</span>
                        <Select value={selectedProfileId} onValueChange={setSelectedProfileId}>
                            <SelectTrigger className="w-64">
                                <SelectValue placeholder={t("sn.fields.profile.select")} />
                            </SelectTrigger>
                            <SelectContent>
                                {profiles.map((profile) => (
                                    <SelectItem key={profile.id} value={profile.id}>
                                        {profile.default ? t("sn.fields.profile.default") : profile.name}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>

                    <SNSiteFieldGridList
                        id={id}
                        statusFields={statusFields}
                        data={data}
                        setSnField={setSnField as React.Dispatch<React.SetStateAction<TurSNSiteField[]>>}
                        onRefreshStatus={refreshStatus}
                        profileId={selectedProfileId}
                        isDefaultProfile={isDefaultProfile}
                    />
                </>
            )}
        </LoadProvider>
    )
}
