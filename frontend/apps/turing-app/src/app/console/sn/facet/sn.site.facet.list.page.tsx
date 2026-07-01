import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteFacetDraggableList } from "@/components/sn/facet/sn.site.facet.draggable.list";
import { SubPageHeader } from "@/components/sub.page.header";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurSNSiteFacetOrdering } from "@/models/sn/sn-site-facet-ordering.model";
import type { TurSNSiteFacetProfile } from "@/models/sn/sn-site-facet-profile.model";
import { TurSNFacetProfileService } from "@/services/sn/sn.facet.profile.service";
import { IconFilter, IconPencil, IconPlus, IconStar, IconTrash } from "@tabler/icons-react";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { toast } from "sonner";

const profileService = new TurSNFacetProfileService();

export default function SNSiteFacetListPage() {
  const { id: siteId } = useParams() as { id: string };
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("sn.facets.title"));

  const [profiles, setProfiles] = useState<TurSNSiteFacetProfile[]>();
  const [selectedProfileId, setSelectedProfileId] = useState<string>();
  const [tableData, setTableData] = useState<TurSNSiteFacetOrdering[]>();
  const [error, setError] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState<"create" | "edit">("create");
  const [formName, setFormName] = useState("");
  const [formDescription, setFormDescription] = useState("");

  const loadProfiles = useCallback(
    async (preferredId?: string) => {
      const list = await profileService.query(siteId);
      setProfiles(list);
      const next =
        preferredId && list.some((profile) => profile.id === preferredId)
          ? preferredId
          : (list.find((profile) => profile.default)?.id ?? list[0]?.id);
      setSelectedProfileId(next);
    },
    [siteId],
  );

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

  const selectedProfile = profiles?.find((profile) => profile.id === selectedProfileId);

  const openCreate = () => {
    setDialogMode("create");
    setFormName("");
    setFormDescription("");
    setDialogOpen(true);
  };

  const openRename = () => {
    if (!selectedProfile || selectedProfile.default) return;
    setDialogMode("edit");
    setFormName(selectedProfile.name);
    setFormDescription(selectedProfile.description ?? "");
    setDialogOpen(true);
  };

  const submitDialog = async () => {
    const name = formName.trim();
    if (!name) return;
    const payload = { name, description: formDescription.trim() || undefined };
    try {
      if (dialogMode === "create") {
        const created = await profileService.create(siteId, payload);
        toast.success(t("sn.facets.profile.created", { name: created.name }));
        await loadProfiles(created.id);
      } else if (selectedProfileId) {
        await profileService.update(siteId, selectedProfileId, payload);
        toast.success(t("sn.facets.profile.updated"));
        await loadProfiles(selectedProfileId);
      }
      setDialogOpen(false);
    } catch {
      toast.error(t("sn.facets.profile.saveError"));
    }
  };

  const handleSetDefault = async () => {
    if (!selectedProfileId) return;
    try {
      await profileService.setDefault(siteId, selectedProfileId);
      toast.success(t("sn.facets.profile.defaultSet"));
      await loadProfiles(selectedProfileId);
    } catch {
      toast.error(t("sn.facets.profile.saveError"));
    }
  };

  const handleDelete = async () => {
    if (!selectedProfile) return;
    if (selectedProfile.default) {
      toast.error(t("sn.facets.profile.cannotDeleteDefault"));
      return;
    }
    if (!window.confirm(t("sn.facets.profile.confirmDelete", { name: selectedProfile.name }))) {
      return;
    }
    try {
      await profileService.remove(siteId, selectedProfile.id);
      toast.success(t("sn.facets.profile.removed"));
      await loadProfiles();
    } catch {
      toast.error(t("sn.facets.profile.deleteError"));
    }
  };

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
          label={t("sn.facets.newCustomFacet")}
          href={`${ROUTES.SN_INSTANCE}/${siteId}/facet/custom/new`}
        />
      </SubPageHeader>

      <div className="px-6 flex flex-wrap items-end gap-2 mb-4">
        <div className="flex flex-col gap-1">
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
        <GradientButton variant="outline" size="sm" onClick={openCreate}>
          <IconPlus className="h-4 w-4" /> {t("sn.facets.profile.new")}
        </GradientButton>
        {/* The default profile is protected: it cannot be renamed, deleted, or unset as default. */}
        {selectedProfile && !selectedProfile.default && (
          <>
            <GradientButton variant="outline" size="sm" onClick={openRename}>
              <IconPencil className="h-4 w-4" /> {t("sn.facets.profile.rename")}
            </GradientButton>
            <GradientButton variant="outline" size="sm" onClick={handleSetDefault}>
              <IconStar className="h-4 w-4" /> {t("sn.facets.profile.setDefault")}
            </GradientButton>
            <GradientButton variant="outline" size="sm" onClick={handleDelete}>
              <IconTrash className="h-4 w-4" /> {t("sn.facets.profile.delete")}
            </GradientButton>
          </>
        )}
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

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {dialogMode === "create"
                ? t("sn.facets.profile.createTitle")
                : t("sn.facets.profile.editTitle")}
            </DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-3 py-2">
            <Input
              value={formName}
              onChange={(event) => setFormName(event.target.value)}
              placeholder={t("sn.facets.profile.namePlaceholder")}
            />
            <Textarea
              value={formDescription}
              onChange={(event) => setFormDescription(event.target.value)}
              placeholder={t("sn.facets.profile.descriptionPlaceholder")}
            />
          </div>
          <DialogFooter>
            <GradientButton variant="outline" size="sm" onClick={() => setDialogOpen(false)}>
              {t("sn.facets.profile.cancel")}
            </GradientButton>
            <GradientButton size="sm" onClick={submitDialog} disabled={!formName.trim()}>
              {t("sn.facets.profile.save")}
            </GradientButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </LoadProvider>
  );
}
