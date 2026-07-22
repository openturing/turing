import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteFacetDraggableList } from "@/components/sn/facet/sn.site.facet.draggable.list";
import { SubPageHeader } from "@/components/sub.page.header";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurProfile } from "@/models/profile/profile.model";
import type { TurSNSiteFacetOrdering } from "@/models/sn/sn-site-facet-ordering.model";
import { TurSNFacetProfileService } from "@/services/sn/sn.facet.profile.service";
import { IconFilter } from "@tabler/icons-react";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const profileService = new TurSNFacetProfileService();

export default function SNSiteFacetListPage() {
  const { id: siteId } = useParams() as { id: string };
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("sn.facets.title"));

  const [profiles, setProfiles] = useState<TurProfile[]>();
  const [selectedProfileId, setSelectedProfileId] = useState<string>();
  const [tableData, setTableData] = useState<TurSNSiteFacetOrdering[]>();
  const [error, setError] = useState<string | null>(null);

  const loadProfiles = useCallback(async () => {
    const list = await profileService.query(siteId);
    setProfiles(list);
    setSelectedProfileId((current) =>
      current && list.some((profile) => profile.id === current)
        ? current
        : (list.find((profile) => profile.default)?.id ?? list[0]?.id),
    );
  }, [siteId]);

  useEffect(() => {
    loadProfiles().catch(() => setError(t("sn.facets.profile.loadError")));
  }, [loadProfiles, t]);

  useEffect(() => {
    if (!selectedProfileId) return;
    setTableData(undefined);
    profileService
      .queryOrdering(siteId, selectedProfileId)
      .then(setTableData)
      .catch(() => setError(t("sn.facets.profile.loadError")));
  }, [siteId, selectedProfileId, t]);

  return (
    <LoadProvider
      checkIsNotUndefined={profiles}
      error={error}
      tryAgainUrl={`${ROUTES.SN_INSTANCE}/${siteId}/facet`}
    >
      <SubPageHeader
        icon={IconFilter}
        name={t("sn.facets.feature")}
        feature={t("sn.facets.feature")}
        description={t("sn.facets.description")}
      >
        <SubPageHeader.Action
          label={t("sn.facets.profile.manage")}
          href={ROUTES.PROFILE_INSTANCE}
        />
        <SubPageHeader.Action
          label={t("sn.facets.newCustomFacet")}
          href={`${ROUTES.SN_INSTANCE}/${siteId}/facet/custom/new`}
        />
      </SubPageHeader>

      <div className="px-6 flex flex-col gap-1 mb-4">
        <span className="text-sm text-muted-foreground">{t("sn.facets.profile.label")}</span>
        <Select value={selectedProfileId} onValueChange={setSelectedProfileId}>
          <SelectTrigger className="w-64">
            <SelectValue placeholder={t("sn.facets.profile.select")} />
          </SelectTrigger>
          <SelectContent>
            {profiles?.map((profile) => (
              <SelectItem key={profile.id} value={profile.id}>
                {profile.default ? t("sn.facets.profile.default") : profile.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {tableData && selectedProfileId && (
        <SNSiteFacetDraggableList
          siteId={siteId}
          tableData={tableData}
          setTableData={
            setTableData as React.Dispatch<React.SetStateAction<TurSNSiteFacetOrdering[]>>
          }
          onSaveOrder={(items) => profileService.saveOrdering(siteId, selectedProfileId, items)}
        />
      )}
    </LoadProvider>
  );
}
