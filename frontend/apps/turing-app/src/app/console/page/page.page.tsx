import { IconCode, IconFileZip, IconGlobe, IconTag, IconUpload, IconUser } from "@tabler/icons-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";

import { DialogDelete } from "@/components/dialog.delete";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import type { TurPageSite } from "@/models/page/page-site.model";
import { TurPageService } from "@/services/page/page.service";

const pageService = new TurPageService();

export default function PagePage() {
  const { t } = useTranslation();
  const [sites, setSites] = useState<TurPageSite[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [deleteOpenFor, setDeleteOpenFor] = useState<string | null>(null);
  const [siteName, setSiteName] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadSites = useCallback(async () => {
    setLoading(true);
    try {
      const data = await pageService.query();
      setSites(data);
    } catch {
      toast.error(t("page.loadError"));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    loadSites();
  }, [loadSites]);

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      const name = file.name.replace(/\.zip$/i, "").replace(/[^a-zA-Z0-9._-]/g, "-").toLowerCase();
      setSiteName(name);
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
    <div className="flex flex-1 flex-col gap-4 p-4 pt-0">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">{t("page.title")}</h2>
          <p className="text-muted-foreground">{t("page.description")}</p>
        </div>
        <div>
          <input
            ref={fileInputRef}
            type="file"
            accept=".zip"
            className="hidden"
            onChange={handleFileSelect}
          />
          <Button onClick={() => fileInputRef.current?.click()}>
            <IconUpload className="mr-2 h-4 w-4" />
            {t("page.uploadButton")}
          </Button>
        </div>
      </div>

      <Separator />

      {loading ? (
        <div className="text-muted-foreground text-center py-8">
          {t("page.loading")}
        </div>
      ) : sites.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <IconGlobe className="h-12 w-12 text-muted-foreground mb-4" />
            <p className="text-muted-foreground text-lg">{t("page.empty")}</p>
            <p className="text-muted-foreground text-sm mt-1">{t("page.emptyHint")}</p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {sites.map((site) => {
            const m = site.manifest;
            return (
            <Card key={site.name}>
              <CardHeader>
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                      <IconGlobe className="h-5 w-5 text-primary" />
                    </div>
                    <div>
                      <CardTitle className="text-base">{site.name}</CardTitle>
                      <CardDescription>
                        {m?.description ? (
                          <span className="line-clamp-2">{m.description}</span>
                        ) : (
                          <Badge variant="outline" className="mt-1">SPA</Badge>
                        )}
                      </CardDescription>
                    </div>
                  </div>
                  <DialogDelete
                    feature={t("page.title")}
                    name={site.name}
                    onDelete={() => handleDelete(site.name)}
                    open={deleteOpenFor === site.name}
                    setOpen={(v) => setDeleteOpenFor(v ? site.name : null)}
                  />
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                <a
                  href={pageService.getSiteUrl(site.name)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-sm text-primary hover:underline flex items-center gap-1"
                >
                  <IconGlobe className="h-3.5 w-3.5" />
                  /pages/{site.name}
                </a>
                {m && (
                  <div className="flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-muted-foreground">
                    {m.version && (
                      <span className="flex items-center gap-1">
                        <IconTag className="h-3 w-3" />
                        v{m.version}
                      </span>
                    )}
                    {m.author && (
                      <span className="flex items-center gap-1">
                        <IconUser className="h-3 w-3" />
                        {m.author}
                      </span>
                    )}
                    {m.framework && (
                      <span className="flex items-center gap-1">
                        <IconCode className="h-3 w-3" />
                        {m.framework}{m.buildTool ? ` + ${m.buildTool}` : ""}
                      </span>
                    )}
                    {m.snSite && (
                      <Badge variant="secondary" className="text-xs h-5">{m.snSite}</Badge>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
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
              <IconFileZip className="h-4 w-4" />
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
              <p className="text-xs text-muted-foreground mt-1">
                {t("page.uploadDialog.siteNameHint", { url: `/pages/${siteName || "my-site"}` })}
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              {t("page.uploadDialog.cancel")}
            </Button>
            <Button onClick={handleUpload} disabled={uploading || !siteName.trim()}>
              {uploading ? t("page.uploadDialog.uploading") : t("page.uploadDialog.deploy")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
