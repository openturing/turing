import { ROUTES } from "@/app/routes.const"
import {
  useCreateSkill,
  useImportSkillZip,
  useSkills,
} from "@/api/queries/skill.queries"
import { LoadProvider } from "@/components/loading-provider"
import { SubPageHeader } from "@/components/sub.page.header"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { GradientButton } from "@/components/ui/gradient-button"
import { Input } from "@/components/ui/input"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { TurSkillService } from "@/services/skill/skill.service"
import {
  IconDownload,
  IconFileImport,
  IconPlus,
  IconSparkles,
} from "@tabler/icons-react"
import { useRef, useState } from "react"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"

const service = new TurSkillService()

export default function SkillPage() {
  const navigate = useNavigate()
  const { data: skills, isError } = useSkills()
  const createSkill = useCreateSkill()
  const importZip = useImportSkillZip()
  const [createOpen, setCreateOpen] = useState(false)
  const [newName, setNewName] = useState("")
  const fileInputRef = useRef<HTMLInputElement>(null)
  useSubPageBreadcrumb("Skills")

  const error = isError ? "Failed to load skills. Check that storage is configured." : null

  const handleCreate = async () => {
    const name = newName.trim()
    if (!name) return
    try {
      const created = await createSkill.mutateAsync(name)
      toast.success(`Skill "${created.name}" created.`)
      setCreateOpen(false)
      setNewName("")
      navigate(`${ROUTES.SKILL_ROOT}/${created.id}`)
    } catch {
      toast.error("Failed to create skill.")
    }
  }

  const handleExport = async (id: string, name: string) => {
    try {
      await service.download(id, name)
    } catch {
      toast.error("Export failed.")
    }
  }

  const handleImport = async (files: FileList | null) => {
    const file = files?.[0]
    if (!file) return
    try {
      const imported = await importZip.mutateAsync(file)
      toast.success(`Imported ${imported.length} skill${imported.length === 1 ? "" : "s"}: ${imported.join(", ")}`)
    } catch {
      toast.error("Import failed. Make sure the ZIP contains a SKILL.md.")
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = ""
    }
  }

  return (
    <LoadProvider checkIsNotUndefined={skills} error={error} tryAgainUrl={ROUTES.SKILL_ROOT}>
      <SubPageHeader
        icon={IconSparkles}
        feature="Skills"
        name="Skills"
        description="Anthropic-compatible skill folders. Create one or import a ZIP, then edit its files."
      />

      <div className="px-4 lg:px-6 space-y-4">
        {/* Toolbar */}
        <div className="flex items-center gap-3">
          <input
            ref={fileInputRef}
            type="file"
            accept=".zip,application/zip"
            className="hidden"
            title="Import skill ZIP"
            onChange={(e) => handleImport(e.target.files)}
          />
          <GradientButton size="sm" onClick={() => { setNewName(""); setCreateOpen(true) }}>
            <IconPlus className="size-4! mr-1.5" />
            New Skill
          </GradientButton>
          <Button
            variant="outline"
            size="sm"
            disabled={importZip.isPending}
            onClick={() => fileInputRef.current?.click()}
          >
            <IconFileImport className="size-4! mr-1.5" />
            {importZip.isPending ? "Importing…" : "Import ZIP"}
          </Button>
        </div>

        {/* Skill list — just the names; click opens the editor */}
        {skills && skills.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
            <IconSparkles className="size-12 mb-3 opacity-40" />
            <p className="text-sm">No skills yet. Create your first skill or import a ZIP.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {skills?.map((skill) => (
              <div
                key={skill.id}
                role="button"
                tabIndex={0}
                onClick={() => navigate(`${ROUTES.SKILL_ROOT}/${skill.id}`)}
                onKeyDown={(e) => { if (e.key === "Enter") navigate(`${ROUTES.SKILL_ROOT}/${skill.id}`) }}
                className="group flex cursor-pointer items-center gap-3 rounded-lg border bg-card px-4 py-3 text-left transition-colors hover:border-blue-500/50 hover:bg-accent"
              >
                <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-linear-to-br from-blue-600 to-indigo-600 text-white">
                  <IconSparkles className="size-4.5" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{skill.name}</span>
                  {skill.version && (
                    <span className="block truncate text-xs text-muted-foreground">v{skill.version}</span>
                  )}
                </span>
                {!skill.enabled && <Badge variant="outline" className="shrink-0">Disabled</Badge>}
                <button
                  type="button"
                  title="Export as ZIP"
                  className="shrink-0 rounded-md p-1.5 text-muted-foreground opacity-0 transition-opacity hover:bg-accent hover:text-foreground group-hover:opacity-100"
                  onClick={(e) => { e.stopPropagation(); handleExport(skill.id, skill.name) }}
                >
                  <IconDownload className="size-4" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* New Skill dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>New Skill</DialogTitle></DialogHeader>
          <Input
            placeholder="Skill name (e.g. brand-content-studio)"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") handleCreate() }}
            autoFocus
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>Cancel</Button>
            <GradientButton onClick={handleCreate} disabled={!newName.trim() || createSkill.isPending}>
              Create
            </GradientButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </LoadProvider>
  )
}
