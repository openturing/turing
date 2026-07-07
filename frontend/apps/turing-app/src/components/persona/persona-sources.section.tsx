"use client"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { SectionCard } from "@/components/ui/section-card"
import type {
  TurPersonaSource,
  TurPersonaSourceStatus,
} from "@/models/persona/persona-source.model.ts"
import { TurPersonaSourceService } from "@/services/persona/persona-source.service"
import {
  IconFileText,
  IconLink,
  IconRefresh,
  IconTrash,
  IconUpload,
  IconWorld,
} from "@tabler/icons-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"

const service = new TurPersonaSourceService()

type AddType = "URL" | "SN_DOC"

const STATUS_STYLES: Record<TurPersonaSourceStatus, string> = {
  EXTRACTED:
    "bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 ring-emerald-500/20",
  PENDING:
    "bg-amber-500/15 text-amber-700 dark:text-amber-400 ring-amber-500/20",
  FAILED: "bg-red-500/15 text-red-700 dark:text-red-400 ring-red-500/20",
}

/**
 * Block AA / §XXVI.2 — the persona "notebook". Self-contained CRUD over
 * `/persona/{id}/source`: add an SN document, a URL, or upload a file; each
 * source's text is extracted server-side and shown as a red/amber/green status
 * with a preview. Rendered only when editing an existing persona (the FK needs
 * a saved persona id).
 */
export const PersonaSourcesSection: React.FC<{ personaId: string }> = ({
  personaId,
}) => {
  const { t } = useTranslation()
  const [sources, setSources] = useState<TurPersonaSource[]>([])
  const [busy, setBusy] = useState(false)
  const [addType, setAddType] = useState<AddType>("URL")
  const [ref, setRef] = useState("")
  const [siteName, setSiteName] = useState("")
  const [sourceName, setSourceName] = useState("")
  const fileInputRef = useRef<HTMLInputElement>(null)

  const refresh = useCallback(() => {
    service
      .query(personaId)
      .then(setSources)
      .catch(() => setSources([]))
  }, [personaId])

  useEffect(() => {
    refresh()
  }, [refresh])

  const onAdd = useCallback(async () => {
    if (!ref.trim()) {
      toast.error(t("forms.persona.sources.refRequired"))
      return
    }
    if (addType === "SN_DOC" && !siteName.trim()) {
      toast.error(t("forms.persona.sources.siteRequired"))
      return
    }
    setBusy(true)
    try {
      const created = await service.createDocOrUrl(personaId, {
        type: addType,
        ref: ref.trim(),
        siteName: addType === "SN_DOC" ? siteName.trim() : null,
        sourceName: sourceName.trim() || null,
      })
      reportExtraction(created)
      setRef("")
      setSiteName("")
      setSourceName("")
      refresh()
    } catch {
      toast.error(t("forms.persona.sources.addFailed"))
    } finally {
      setBusy(false)
    }
  }, [addType, personaId, ref, siteName, sourceName, refresh, t])

  const onUpload = useCallback(
    async (file: File) => {
      setBusy(true)
      try {
        const created = await service.upload(personaId, file)
        reportExtraction(created)
        refresh()
      } catch {
        toast.error(t("forms.persona.sources.addFailed"))
      } finally {
        setBusy(false)
        if (fileInputRef.current) fileInputRef.current.value = ""
      }
    },
    [personaId, refresh, t]
  )

  const onReextract = useCallback(
    async (id: string) => {
      setBusy(true)
      try {
        reportExtraction(await service.reextract(personaId, id))
        refresh()
      } finally {
        setBusy(false)
      }
    },
    [personaId, refresh]
  )

  const onDelete = useCallback(
    async (id: string) => {
      setBusy(true)
      try {
        await service.delete(personaId, id)
        refresh()
      } finally {
        setBusy(false)
      }
    },
    [personaId, refresh]
  )

  function reportExtraction(source: TurPersonaSource) {
    if (source.extractionStatus === "EXTRACTED") {
      toast.success(
        t("forms.persona.sources.extracted", {
          count: source.cachedTextLength ?? 0,
        })
      )
    } else {
      toast.error(
        source.extractionError ?? t("forms.persona.sources.addFailed")
      )
    }
  }

  return (
    <SectionCard variant="cyan">
      <SectionCard.Header
        icon={IconFileText}
        title={t("forms.persona.sources.title")}
        description={t("forms.persona.sources.desc")}
      />
      <SectionCard.Content>
        {/* Existing sources */}
        {sources.length === 0 ? (
          <p className="text-muted-foreground text-sm">
            {t("forms.persona.sources.empty")}
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {sources.map((s) => (
              <li
                key={s.id}
                className="flex items-start gap-3 rounded-lg border p-3"
              >
                <SourceTypeIcon type={s.type} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="truncate font-medium">
                      {s.sourceName || s.ref}
                    </span>
                    <span
                      className={`shrink-0 rounded-full px-2 py-0.5 text-xs ring-1 ${STATUS_STYLES[s.extractionStatus]}`}
                    >
                      {t(`persona.sourceStatus.${s.extractionStatus}`)}
                    </span>
                  </div>
                  <div className="text-muted-foreground truncate text-xs">
                    {s.type === "SN_DOC" && s.siteName
                      ? `${s.siteName} · ${s.ref}`
                      : s.ref}
                    {s.cachedTextLength
                      ? ` · ${s.cachedTextLength.toLocaleString()} ${t("forms.persona.sources.chars")}`
                      : ""}
                  </div>
                  {s.extractionStatus === "FAILED" && s.extractionError && (
                    <div className="text-xs text-red-600 dark:text-red-400">
                      {s.extractionError}
                    </div>
                  )}
                  {s.cachedTextPreview && (
                    <p className="text-muted-foreground mt-1 line-clamp-2 text-xs italic">
                      {s.cachedTextPreview}
                    </p>
                  )}
                </div>
                <div className="flex shrink-0 gap-1">
                  {s.type !== "ASSET" && (
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      disabled={busy}
                      title={t("forms.persona.sources.reextract")}
                      onClick={() => onReextract(s.id)}
                    >
                      <IconRefresh className="size-4" />
                    </Button>
                  )}
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    disabled={busy}
                    title={t("forms.persona.sources.delete")}
                    onClick={() => onDelete(s.id)}
                  >
                    <IconTrash className="size-4 text-red-600" />
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}

        {/* Add new */}
        <div className="mt-4 flex flex-col gap-2 rounded-lg border border-dashed p-3">
          <div className="flex flex-col gap-2 sm:flex-row">
            <Select
              value={addType}
              onValueChange={(v) => setAddType(v as AddType)}
            >
              <SelectTrigger className="w-full sm:w-44">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="URL">
                  {t("persona.sourceTypes.URL")}
                </SelectItem>
                <SelectItem value="SN_DOC">
                  {t("persona.sourceTypes.SN_DOC")}
                </SelectItem>
              </SelectContent>
            </Select>
            <Input
              value={ref}
              onChange={(e) => setRef(e.target.value)}
              placeholder={
                addType === "URL"
                  ? t("forms.persona.sources.urlPlaceholder")
                  : t("forms.persona.sources.docIdPlaceholder")
              }
            />
          </div>
          {addType === "SN_DOC" && (
            <Input
              value={siteName}
              onChange={(e) => setSiteName(e.target.value)}
              placeholder={t("forms.persona.sources.sitePlaceholder")}
            />
          )}
          <Input
            value={sourceName}
            onChange={(e) => setSourceName(e.target.value)}
            placeholder={t("forms.persona.sources.namePlaceholder")}
          />
          <div className="flex flex-wrap gap-2">
            <Button type="button" disabled={busy} onClick={onAdd}>
              <IconLink className="size-4" />
              {t("forms.persona.sources.add")}
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={busy}
              onClick={() => fileInputRef.current?.click()}
            >
              <IconUpload className="size-4" />
              {t("forms.persona.sources.upload")}
            </Button>
            <input
              ref={fileInputRef}
              type="file"
              className="hidden"
              accept=".pdf,.doc,.docx,.txt,.md,.html,.csv,.xls,.xlsx"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) onUpload(file)
              }}
            />
          </div>
        </div>
      </SectionCard.Content>
    </SectionCard>
  )
}

function SourceTypeIcon({
  type,
}: {
  type: TurPersonaSource["type"]
}): React.ReactElement {
  const className = "size-5 shrink-0 text-muted-foreground mt-0.5"
  if (type === "URL") return <IconWorld className={className} />
  if (type === "SN_DOC") return <IconWorld className={className} />
  return <IconFileText className={className} />
}
