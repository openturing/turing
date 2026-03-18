import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { Button } from "@/components/ui/button";
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurAssetItem } from "@/models/asset/asset-item.model";
import { TurAssetService } from "@/services/asset/asset.service";
import {
  IconChevronRight,
  IconCloudUpload,
  IconDownload,
  IconFile,
  IconFolder,
  IconFolderPlus,
  IconHome,
  IconTrash,
} from "@tabler/icons-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";

const turAssetService = new TurAssetService();

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024)
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function formatDate(iso: string): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

/** Extracts display name from full object path (removes trailing slash for dirs). */
function displayName(fullPath: string, prefix: string): string {
  const relative = fullPath.startsWith(prefix)
    ? fullPath.slice(prefix.length)
    : fullPath;
  return relative.replace(/\/$/, "");
}

/** Splits prefix into breadcrumb segments. */
function prefixSegments(prefix: string): { label: string; path: string }[] {
  if (!prefix) return [];
  const parts = prefix.replace(/\/$/, "").split("/");
  const segments: { label: string; path: string }[] = [];
  for (let i = 0; i < parts.length; i++) {
    segments.push({
      label: parts[i],
      path: parts.slice(0, i + 1).join("/") + "/",
    });
  }
  return segments;
}

export default function AssetPage() {
  const [assets, setAssets] = useState<TurAssetItem[]>();
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [prefix, setPrefix] = useState("");
  const [folderDialogOpen, setFolderDialogOpen] = useState(false);
  const [newFolderName, setNewFolderName] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);
  useSubPageBreadcrumb("Assets");

  const loadAssets = useCallback(() => {
    setError(null);
    setAssets(undefined);
    turAssetService
      .query(prefix)
      .then(setAssets)
      .catch(() => setError("Failed to load assets. Check MinIO connection."));
  }, [prefix]);

  useEffect(() => {
    loadAssets();
  }, [loadAssets]);

  const handleUpload = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setUploading(true);
    try {
      await turAssetService.upload(Array.from(files), prefix);
      toast.success(
        `${files.length} file${files.length > 1 ? "s" : ""} uploaded.`
      );
      loadAssets();
    } catch {
      toast.error("Upload failed.");
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleCreateFolder = async () => {
    const name = newFolderName.trim();
    if (!name) return;
    try {
      await turAssetService.createFolder(prefix + name);
      toast.success(`Folder "${name}" created.`);
      setFolderDialogOpen(false);
      setNewFolderName("");
      loadAssets();
    } catch {
      toast.error("Failed to create folder.");
    }
  };

  const handleDelete = async (objectName: string) => {
    try {
      await turAssetService.delete(objectName);
      toast.success("Deleted.");
      loadAssets();
    } catch {
      toast.error("Delete failed.");
    }
  };

  const handleDownload = (objectName: string) => {
    const url = turAssetService.downloadUrl(objectName);
    const a = document.createElement("a");
    a.href = url;
    a.download = objectName.split("/").pop() ?? objectName;
    document.body.appendChild(a);
    a.click();
    a.remove();
  };

  const navigateToFolder = (folderPath: string) => {
    setPrefix(folderPath);
  };

  const breadcrumbs = prefixSegments(prefix);

  return (
    <LoadProvider
      checkIsNotUndefined={assets}
      error={error}
      tryAgainUrl={ROUTES.ASSET_ROOT}
    >
      <SubPageHeader
        icon={IconFolder}
        feature="Assets"
        name="Assets"
        description="Manage files stored in MinIO."
      />

      <div className="px-4 lg:px-6 space-y-4">
        {/* Toolbar */}
        <div className="flex items-center gap-3">
          <input
            ref={fileInputRef}
            type="file"
            multiple
            className="hidden"
            onChange={(e) => handleUpload(e.target.files)}
          />
          <GradientButton
            size="sm"
            disabled={uploading}
            onClick={() => fileInputRef.current?.click()}
          >
            <IconCloudUpload className="size-4! mr-1.5" />
            {uploading ? "Uploading…" : "Upload Files"}
          </GradientButton>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setNewFolderName("");
              setFolderDialogOpen(true);
            }}
          >
            <IconFolderPlus className="size-4! mr-1.5" />
            New Folder
          </Button>
        </div>

        {/* Breadcrumb navigation */}
        <nav className="flex items-center gap-1 text-sm text-muted-foreground">
          <button
            type="button"
            onClick={() => navigateToFolder("")}
            className={`flex items-center gap-1 px-1.5 py-0.5 rounded hover:bg-accent hover:text-foreground transition-colors ${
              prefix === "" ? "text-foreground font-medium" : ""
            }`}
          >
            <IconHome className="size-3.5" />
            Root
          </button>
          {breadcrumbs.map((seg) => (
            <span key={seg.path} className="flex items-center gap-1">
              <IconChevronRight className="size-3 text-muted-foreground/50" />
              <button
                type="button"
                onClick={() => navigateToFolder(seg.path)}
                className={`px-1.5 py-0.5 rounded hover:bg-accent hover:text-foreground transition-colors ${
                  seg.path === prefix ? "text-foreground font-medium" : ""
                }`}
              >
                {seg.label}
              </button>
            </span>
          ))}
        </nav>

        {/* Assets table */}
        {assets && assets.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
            <IconFile className="size-12 mb-3 opacity-40" />
            <p className="text-sm">
              {prefix
                ? "This folder is empty."
                : "No assets found. Upload your first file."}
            </p>
          </div>
        ) : (
          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead className="w-[100px]">Size</TableHead>
                  <TableHead className="w-[120px]">Type</TableHead>
                  <TableHead className="w-[180px]">Last Modified</TableHead>
                  <TableHead className="w-[100px] text-right">
                    Actions
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {assets?.map((asset) => (
                  <TableRow key={asset.name}>
                    <TableCell className="font-medium">
                      {asset.directory ? (
                        <button
                          type="button"
                          className="flex items-center gap-2 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                          onClick={() => navigateToFolder(asset.name)}
                        >
                          <IconFolder className="size-4 text-blue-500 shrink-0" />
                          <span className="truncate max-w-[400px]">
                            {displayName(asset.name, prefix)}
                          </span>
                        </button>
                      ) : (
                        <div className="flex items-center gap-2">
                          <IconFile className="size-4 text-muted-foreground shrink-0" />
                          <span className="truncate max-w-[400px]">
                            {displayName(asset.name, prefix)}
                          </span>
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {asset.directory ? "—" : formatFileSize(asset.size)}
                    </TableCell>
                    <TableCell className="text-muted-foreground text-xs">
                      {asset.directory ? "Folder" : asset.contentType}
                    </TableCell>
                    <TableCell className="text-muted-foreground text-xs">
                      {asset.directory ? "—" : formatDate(asset.lastModified)}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex items-center justify-end gap-1">
                        {!asset.directory && (
                          <button
                            type="button"
                            className="p-1.5 rounded-md hover:bg-accent text-muted-foreground hover:text-foreground transition-colors"
                            title="Download"
                            onClick={() => handleDownload(asset.name)}
                          >
                            <IconDownload className="size-4" />
                          </button>
                        )}
                        <button
                          type="button"
                          className="p-1.5 rounded-md hover:bg-destructive/10 text-muted-foreground hover:text-destructive transition-colors"
                          title="Delete"
                          onClick={() => handleDelete(asset.name)}
                        >
                          <IconTrash className="size-4" />
                        </button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </div>

      {/* New Folder Dialog */}
      <Dialog open={folderDialogOpen} onOpenChange={setFolderDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New Folder</DialogTitle>
          </DialogHeader>
          <Input
            placeholder="Folder name"
            value={newFolderName}
            onChange={(e) => setNewFolderName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleCreateFolder();
            }}
            autoFocus
          />
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setFolderDialogOpen(false)}
            >
              Cancel
            </Button>
            <GradientButton
              onClick={handleCreateFolder}
              disabled={!newFolderName.trim()}
            >
              Create
            </GradientButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </LoadProvider>
  );
}
