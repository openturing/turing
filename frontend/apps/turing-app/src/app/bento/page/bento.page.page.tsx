import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { DialogDelete } from "@/components/dialog.delete";
import { LoadProvider } from "@/components/loading-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import type { TurPageSite } from "@/models/page/page-site.model";
import { TurPageService } from "@/services/page/page.service";
import { IconCode, IconFileZip, IconGlobe, IconTag, IconTrash, IconUpload, IconUser } from "@tabler/icons-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";

const pageService = new TurPageService();

/**
 * Bento Page CRUD — T562. A "page" is a ZIP-deployed SPA (upload / open /
 * delete, no edit form), so instead of the hero/save-bar entity scaffold this
 * re-skins the console site grid onto a `BentoHero` + frosted tiles. Reuses
 * `TurPageService` (upload/delete/query) verbatim; folder-less, route-less.
 */
export default function BentoPagePage() {
  const { t } = useTranslation();
  const [sites, setSites] = useState<TurPageSite[]>();
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [deleteOpenFor, setDeleteOpenFor] = useState<string | null>(null);
  const [siteName, setSiteName] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadSites = useCallback(async () => {
    setError(null);
    try {
      setSites(await pageService.query());
    } catch {
      setError(t("page.loadError"));
    }
  }, [t]);

  useEffect(() => { loadSites(); }, [loadSites]);

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      setSiteName(file.name.replace(/\.zip$/i, "").replace(/[^a-zA-Z0-9._-]/g, "-").toLowerCase());
      setDialogOpen(true);
    }
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const handleUpload = async () => {
    if (!selectedFile || !siteName.trim()) return;
    setUploading(true);
    try {
      await pageService.upload(selectedFile, siteName.trim());
      toast.success(t("page.uploadSuccess", { name: siteName }));
      setDialogOpen(false);
      setSelectedFile(null);
      setSiteName("");
      await loadSites();
    } catch {
      toast.error(t("page.uploadError"));
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (name: string) => {
    try {
      await pageService.delete(name);
      toast.success(t("page.deleteSuccess", { name }));
      setDeleteOpenFor(null);
      await loadSites();
    } catch {
      toast.error(t("page.deleteError"));
    }
  };

  return (
    <LoadProvider checkIsNotUndefined={sites} error={error} tryAgainUrl={ROUTES.BENTO_PAGE}>
      <input
        ref={fileInputRef}
        type="file"
        accept=".zip"
        className="hidden"
        aria-label={t("page.uploadButton")}
        onChange={handleFileSelect}
      />
      <BentoHero
        backTo={ROUTES.BENTO_AREA_MANAGEMENT}
        backLabel={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-blue-600 to-indigo-600 text-white shadow-md">
            <IconGlobe size={24} />
          </span>
        }
        title={t("page.title")}
        subtitle={t("page.description")}
        trailing={
          <GradientButton size="sm" onClick={() => fileInputRef.current?.click()}>
            <IconUpload className="size-4" />
            {t("page.uploadButton")}
          </GradientButton>
        }
      />

      {sites && sites.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
          <IconGlobe className="size-12 mb-3 opacity-40" />
          <p className="text-lg">{t("page.empty")}</p>
          <p className="text-sm mt-1">{t("page.emptyHint")}</p>
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {sites?.map((site) => {
            const m = site.manifest;
            return (
              <div key={site.name} className="bento-tile bento-glass flex flex-col gap-3 rounded-2xl p-5">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-3 min-w-0">
                    <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-linear-to-br from-blue-600 to-indigo-600 text-white shadow-sm">
                      <IconGlobe className="size-5" />
                    </span>
                    <div className="min-w-0">
                      <div className="truncate font-semibold">{site.name}</div>
                      {m?.description ? (
                        <p className="line-clamp-2 text-sm text-muted-foreground">{m.description}</p>
                      ) : (
                        <Badge variant="outline" className="mt-1">SPA</Badge>
                      )}
                    </div>
                  </div>
                  <button
                    type="button"
                    title={t("forms.formActions.delete")}
                    className="shrink-0 rounded-md p-1.5 text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                    onClick={() => setDeleteOpenFor(site.name)}
                  >
                    <IconTrash className="size-4" />
                  </button>
                  <DialogDelete
                    feature={t("page.title")}
                    name={site.name}
                    onDelete={() => handleDelete(site.name)}
                    open={deleteOpenFor === site.name}
                    setOpen={(v) => setDeleteOpenFor(v ? site.name : null)}
                    trigger={<span className="hidden" aria-hidden />}
                  />
                </div>
                <a
                  href={pageService.getSiteUrl(site.name)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-1 text-sm text-primary hover:underline"
                >
                  <IconGlobe className="size-3.5" />
                  /pages/{site.name}
                </a>
                {m && (
                  <div className="flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-muted-foreground">
                    {m.version && <span className="flex items-center gap-1"><IconTag className="size-3" />v{m.version}</span>}
                    {m.author && <span className="flex items-center gap-1"><IconUser className="size-3" />{m.author}</span>}
                    {m.framework && (
                      <span className="flex items-center gap-1">
                        <IconCode className="size-3" />
                        {m.framework}{m.buildTool ? ` + ${m.buildTool}` : ""}
                      </span>
                    )}
                    {m.snSite && <Badge variant="secondary" className="h-5 text-xs">{m.snSite}</Badge>}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("page.uploadDialog.title")}</DialogTitle>
            <DialogDescription>{t("page.uploadDialog.description")}</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <IconFileZip className="size-4" />
              {selectedFile?.name}
            </div>
            <div>
              <label className="text-sm font-medium">{t("page.uploadDialog.siteNameLabel")}</label>
              <Input
                className="mt-1.5"
                value={siteName}
                onChange={(e) => setSiteName(e.target.value)}
                placeholder={t("page.uploadDialog.siteNamePlaceholder")}
              />
              <p className="mt-1 text-xs text-muted-foreground">
                {t("page.uploadDialog.siteNameHint", { url: `/pages/${siteName || "my-site"}` })}
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>{t("page.uploadDialog.cancel")}</Button>
            <GradientButton onClick={handleUpload} disabled={uploading || !siteName.trim()}>
              {uploading ? t("page.uploadDialog.uploading") : t("page.uploadDialog.deploy")}
            </GradientButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </LoadProvider>
  );
}
