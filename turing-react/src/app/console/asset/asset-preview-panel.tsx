import { Button } from "@/components/ui/button"
import type { TurAssetItem } from "@/models/asset/asset-item.model"
import { TurAssetService } from "@/services/asset/asset.service"
import {
  IconDownload,
  IconFile,
  IconFileText,
  IconMaximize,
  IconMusic,
  IconPhoto,
  IconVideo,
  IconX,
} from "@tabler/icons-react"
import { useCallback, useEffect, useState } from "react"
import { createPortal } from "react-dom"

interface AssetPreviewPanelProps {
  asset: TurAssetItem
  onClose: () => void
  onDownload: (objectName: string) => void
}

const turAssetService = new TurAssetService()

const IMAGE_TYPES = new Set(["image/png", "image/jpeg", "image/gif", "image/webp", "image/svg+xml", "image/bmp"])
const PDF_TYPE = "application/pdf"
const VIDEO_TYPES = new Set(["video/mp4", "video/webm", "video/ogg"])
const AUDIO_TYPES = new Set(["audio/mpeg", "audio/ogg", "audio/wav", "audio/webm", "audio/mp3"])
const TEXT_TYPES = new Set([
  "text/plain", "text/csv", "text/html", "text/css", "text/javascript",
  "application/json", "application/xml", "application/javascript",
])

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

function formatDate(iso: string): string {
  if (!iso) return "—"
  try { return new Date(iso).toLocaleString() } catch { return iso }
}

function fileName(fullPath: string): string {
  const parts = fullPath.replace(/\/$/, "").split("/")
  return parts[parts.length - 1]
}

function fileExtension(name: string): string {
  const dot = name.lastIndexOf(".")
  return dot >= 0 ? name.slice(dot + 1).toUpperCase() : ""
}

function typeIcon(contentType: string) {
  if (IMAGE_TYPES.has(contentType)) return <IconPhoto className="size-5 text-blue-500" />
  if (contentType === PDF_TYPE) return <IconFileText className="size-5 text-red-500" />
  if (VIDEO_TYPES.has(contentType)) return <IconVideo className="size-5 text-purple-500" />
  if (AUDIO_TYPES.has(contentType)) return <IconMusic className="size-5 text-emerald-500" />
  if (TEXT_TYPES.has(contentType)) return <IconFileText className="size-5 text-amber-500" />
  return <IconFile className="size-5 text-muted-foreground" />
}

function PreviewContent({ asset }: Readonly<{ asset: TurAssetItem }>) {
  const url = turAssetService.previewUrl(asset.name)
  const ct = asset.contentType

  if (IMAGE_TYPES.has(ct)) {
    return (
      <div className="flex items-center justify-center h-full bg-muted/30 p-4">
        <img src={url} alt={fileName(asset.name)} className="max-w-full max-h-full object-contain rounded" />
      </div>
    )
  }

  if (ct === PDF_TYPE) {
    return <iframe src={url} className="w-full h-full border-0" title={fileName(asset.name)} />
  }

  if (VIDEO_TYPES.has(ct)) {
    return (
      <div className="flex items-center justify-center h-full bg-black p-4">
        <video src={url} controls className="max-w-full max-h-full rounded">
          <track kind="captions" />
        </video>
      </div>
    )
  }

  if (AUDIO_TYPES.has(ct)) {
    return (
      <div className="flex items-center justify-center h-full bg-muted/30 p-8">
        <audio src={url} controls className="w-full">
          <track kind="captions" />
        </audio>
      </div>
    )
  }

  if (TEXT_TYPES.has(ct)) {
    return <iframe src={url} className="w-full h-full border-0 bg-white" title={fileName(asset.name)} />
  }

  return (
    <div className="flex flex-col items-center justify-center h-full text-muted-foreground gap-3">
      <IconFile className="size-16 opacity-30" />
      <p className="text-sm">Preview not available for this file type.</p>
      <p className="text-xs">{ct}</p>
    </div>
  )
}

function FullscreenPreview({ asset, onClose, onDownload }: Readonly<{ asset: TurAssetItem; onClose: () => void; onDownload: (name: string) => void }>) {
  const handleKey = useCallback((e: KeyboardEvent) => { if (e.key === "Escape") onClose() }, [onClose])
  useEffect(() => {
    document.addEventListener("keydown", handleKey)
    return () => document.removeEventListener("keydown", handleKey)
  }, [handleKey])

  return createPortal(
    <div className="fixed inset-0 z-50 flex flex-col bg-background">
      <div className="flex items-center justify-between border-b px-4 py-2 shrink-0">
        <div className="flex items-center gap-2 min-w-0">
          {typeIcon(asset.contentType)}
          <span className="font-medium text-sm truncate">{fileName(asset.name)}</span>
        </div>
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="icon" title="Download" onClick={() => onDownload(asset.name)}>
            <IconDownload className="size-4" />
          </Button>
          <Button variant="ghost" size="icon" title="Close" onClick={onClose}>
            <IconX className="size-5" />
          </Button>
        </div>
      </div>
      <div className="flex-1 min-h-0 overflow-hidden">
        <PreviewContent asset={asset} />
      </div>
    </div>,
    document.body,
  )
}

export function AssetPreviewPanel({ asset, onClose, onDownload }: Readonly<AssetPreviewPanelProps>) {
  const name = fileName(asset.name)
  const ext = fileExtension(name)
  const [fullscreen, setFullscreen] = useState(false)

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between border-b px-4 py-3 shrink-0">
        <div className="flex items-center gap-2 min-w-0">
          {typeIcon(asset.contentType)}
          <span className="font-medium text-sm truncate">{name}</span>
        </div>
        <div className="flex items-center gap-1 shrink-0">
          <Button variant="ghost" size="icon" title="Fullscreen" onClick={() => setFullscreen(true)}>
            <IconMaximize className="size-4" />
          </Button>
          <Button variant="ghost" size="icon" title="Download" onClick={() => onDownload(asset.name)}>
            <IconDownload className="size-4" />
          </Button>
          <Button variant="ghost" size="icon" title="Close" onClick={onClose}>
            <IconX className="size-4" />
          </Button>
        </div>
      </div>
      {fullscreen && <FullscreenPreview asset={asset} onClose={() => setFullscreen(false)} onDownload={onDownload} />}

      {/* Preview area */}
      <div className="flex-1 min-h-0 overflow-hidden">
        <PreviewContent asset={asset} />
      </div>

      {/* Metadata footer */}
      <div className="border-t px-4 py-3 shrink-0 grid grid-cols-2 gap-x-6 gap-y-1.5 text-xs">
        <div>
          <span className="text-muted-foreground">Size</span>
          <p className="font-medium">{formatFileSize(asset.size)}</p>
        </div>
        <div>
          <span className="text-muted-foreground">Type</span>
          <p className="font-medium">{asset.contentType}</p>
        </div>
        <div>
          <span className="text-muted-foreground">Last Modified</span>
          <p className="font-medium">{formatDate(asset.lastModified)}</p>
        </div>
        {ext && (
          <div>
            <span className="text-muted-foreground">Extension</span>
            <p className="font-medium">.{ext.toLowerCase()}</p>
          </div>
        )}
      </div>
    </div>
  )
}
