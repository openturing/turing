import { LoadProvider } from "@/components/loading-provider";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import type { TurProfile } from "@/models/profile/profile.model";
import { TurProfileService } from "@/services/profile/profile.service";
import { IconPencil, IconPlus, IconStar, IconStarFilled, IconTrash } from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

const profileService = new TurProfileService();

export default function ProfileListPage() {
  const { t } = useTranslation();

  const [profiles, setProfiles] = useState<TurProfile[]>();
  const [error, setError] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formName, setFormName] = useState("");
  const [formDescription, setFormDescription] = useState("");

  const load = useCallback(async () => {
    setProfiles(await profileService.query());
  }, []);

  useEffect(() => {
    load().catch(() => setError(t("profile.loadError")));
  }, [load, t]);

  const openCreate = () => {
    setEditingId(null);
    setFormName("");
    setFormDescription("");
    setDialogOpen(true);
  };

  const openEdit = (profile: TurProfile) => {
    setEditingId(profile.id);
    setFormName(profile.name);
    setFormDescription(profile.description ?? "");
    setDialogOpen(true);
  };

  const submitDialog = async () => {
    const name = formName.trim();
    if (!name) return;
    const payload = { name, description: formDescription.trim() || undefined };
    try {
      if (editingId) {
        await profileService.update(editingId, payload);
        toast.success(t("profile.updated"));
      } else {
        const created = await profileService.create(payload);
        toast.success(t("profile.created", { name: created.name }));
      }
      setDialogOpen(false);
      await load();
    } catch {
      toast.error(t("profile.saveError"));
    }
  };

  const handleSetDefault = async (profile: TurProfile) => {
    try {
      await profileService.setDefault(profile.id);
      toast.success(t("profile.defaultSet"));
      await load();
    } catch {
      toast.error(t("profile.saveError"));
    }
  };

  const handleDelete = async (profile: TurProfile) => {
    if (!window.confirm(t("profile.confirmDelete", { name: profile.name }))) return;
    try {
      await profileService.remove(profile.id);
      toast.success(t("profile.removed"));
      await load();
    } catch {
      toast.error(t("profile.deleteError"));
    }
  };

  const dialogTitle = editingId ? t("profile.editTitle") : t("profile.createTitle");
  const isEditingDefault = !!editingId && !!profiles?.find((p) => p.id === editingId)?.default;

  return (
    <LoadProvider checkIsNotUndefined={profiles} error={error}>
      <div className="px-6 py-4">
        <div className="mb-4 flex items-center justify-between gap-2">
          <p className="text-sm text-muted-foreground">{t("profile.description")}</p>
          <GradientButton size="sm" onClick={openCreate}>
            <IconPlus className="h-4 w-4" /> {t("profile.new")}
          </GradientButton>
        </div>

        <div className="flex flex-col gap-2">
          {profiles?.map((profile) => (
            <div
              key={profile.id}
              className="flex items-center justify-between gap-3 rounded-lg border border-border bg-card px-4 py-3"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium truncate">{profile.name}</span>
                  {profile.default && (
                    <span className="inline-flex items-center gap-1 rounded-full bg-blue-500/10 px-2 py-0.5 text-xs text-blue-600 dark:text-blue-400">
                      <IconStarFilled className="h-3 w-3" /> {t("profile.default")}
                    </span>
                  )}
                </div>
                {profile.description && (
                  <p className="text-sm text-muted-foreground truncate">{profile.description}</p>
                )}
              </div>

              <div className="flex shrink-0 items-center gap-1">
                {/* The default profile is protected: it cannot be set-default-off, renamed, or deleted. */}
                {!profile.default && (
                  <GradientButton
                    variant="outline"
                    size="sm"
                    onClick={() => handleSetDefault(profile)}
                    title={t("profile.setDefault")}
                  >
                    <IconStar className="h-4 w-4" />
                  </GradientButton>
                )}
                <GradientButton
                  variant="outline"
                  size="sm"
                  onClick={() => openEdit(profile)}
                  title={t("profile.edit")}
                >
                  <IconPencil className="h-4 w-4" />
                </GradientButton>
                {!profile.default && (
                  <GradientButton
                    variant="outline"
                    size="sm"
                    onClick={() => handleDelete(profile)}
                    title={t("profile.delete")}
                  >
                    <IconTrash className="h-4 w-4" />
                  </GradientButton>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{dialogTitle}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-3 py-2">
            <Input
              value={formName}
              onChange={(event) => setFormName(event.target.value)}
              placeholder={t("profile.namePlaceholder")}
              disabled={isEditingDefault}
            />
            {isEditingDefault && (
              <p className="text-xs text-muted-foreground">{t("profile.cannotRenameDefault")}</p>
            )}
            <Textarea
              value={formDescription}
              onChange={(event) => setFormDescription(event.target.value)}
              placeholder={t("profile.descriptionPlaceholder")}
            />
          </div>
          <DialogFooter>
            <GradientButton variant="outline" size="sm" onClick={() => setDialogOpen(false)}>
              {t("profile.cancel")}
            </GradientButton>
            <GradientButton size="sm" onClick={submitDialog} disabled={!formName.trim()}>
              {t("profile.save")}
            </GradientButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </LoadProvider>
  );
}
