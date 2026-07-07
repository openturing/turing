import { ROUTES } from "@/app/routes.const"
import { queryKeys } from "@/api/queries/keys"
import { useSkill, useSkillFiles } from "@/api/queries/skill.queries"
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
import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup,
} from "@/components/ui/resizable"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import type { TurSkillFileNode } from "@/models/skill/skill.model"
import { TurSkillService } from "@/services/skill/skill.service"
import { StreamLanguage } from "@codemirror/language"
import { javascript } from "@codemirror/legacy-modes/mode/javascript"
import { python } from "@codemirror/legacy-modes/mode/python"
import { shell } from "@codemirror/legacy-modes/mode/shell"
import { yaml } from "@codemirror/legacy-modes/mode/yaml"
import { xml } from "@codemirror/legacy-modes/mode/xml"
import { css } from "@codemirror/legacy-modes/mode/css"
import { oneDark } from "@codemirror/theme-one-dark"
import {
  IconArrowLeft,
  IconChevronDown,
  IconChevronRight,
  IconDeviceFloppy,
  IconDownload,
  IconFile,
  IconFilePlus,
  IconFolder,
  IconFolderPlus,
  IconLoader2,
  IconSparkles,
  IconTrash,
} from "@tabler/icons-react"
import CodeMirror from "@uiw/react-codemirror"
import type { Extension } from "@codemirror/state"
import { useQueryClient } from "@tanstack/react-query"
import { useEffect, useMemo, useRef, useState } from "react"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"

const service = new TurSkillService()

/** Pick a CodeMirror language for the given file name; falls back to plain text. */
function languageFor(name: string): Extension[] {
  const ext = name.toLowerCase().split(".").pop() ?? ""
  switch (ext) {
    case "py":
      return [StreamLanguage.define(python)]
    case "js":
    case "ts":
    case "jsx":
    case "tsx":
    case "json":
      return [StreamLanguage.define(javascript)]
    case "sh":
    case "bash":
      return [StreamLanguage.define(shell)]
    case "yaml":
    case "yml":
      return [StreamLanguage.define(yaml)]
    case "xml":
    case "html":
    case "htm":
      return [StreamLanguage.define(xml)]
    case "css":
      return [StreamLanguage.define(css)]
    default:
      return []
  }
}

/** A node of the rendered tree (folders carry children). */
interface TreeNode {
  path: string
  name: string
  directory: boolean
  children: TreeNode[]
}

/** Build a nested tree from the flat, skill-root-relative node list. */
function buildTree(nodes: TurSkillFileNode[]): TreeNode[] {
  const map = new Map<string, TreeNode>()
  const roots: TreeNode[] = []
  // Folders first so parents exist before children attach.
  const sorted = [...nodes].sort((a, b) => a.path.split("/").length - b.path.split("/").length)
  for (const node of sorted) {
    const tn: TreeNode = { path: node.path, name: node.name, directory: node.directory, children: [] }
    map.set(node.path, tn)
    const slash = node.path.lastIndexOf("/")
    const parent = slash >= 0 ? map.get(node.path.slice(0, slash)) : undefined
    if (parent) parent.children.push(tn)
    else roots.push(tn)
  }
  const sortTree = (list: TreeNode[]) => {
    list.sort((a, b) => (a.directory === b.directory ? a.name.localeCompare(b.name) : a.directory ? -1 : 1))
    list.forEach((n) => sortTree(n.children))
  }
  sortTree(roots)
  return roots
}

interface SkillEditorPageProps {
  /**
   * Skills list route the "back" button + save redirects navigate to. Defaults
   * to the console (`ROUTES.SKILL_ROOT`); the Bento skill editor (T560) passes
   * `ROUTES.BENTO_SKILL` so it stays inside the shell.
   */
  baseRoute?: string
  /**
   * Root height/frame classes. Defaults to the console full-viewport IDE; the
   * Bento variant passes a shell-aware height + a frosted frame so the editor
   * fits inside the padded bento main column instead of overflowing it.
   */
  containerClassName?: string
}

export default function SkillEditorPage({
  baseRoute = ROUTES.SKILL_ROOT,
  containerClassName = "flex flex-col h-[calc(100vh-3.5rem)]",
}: Readonly<SkillEditorPageProps> = {}) {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: skill } = useSkill(id)
  const { data: files } = useSkillFiles(id)

  const [selectedPath, setSelectedPath] = useState<string | null>(null)
  const [content, setContent] = useState("")
  const [dirty, setDirty] = useState(false)
  const [loadingFile, setLoadingFile] = useState(false)
  const [saving, setSaving] = useState(false)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())

  const [dialog, setDialog] = useState<null | "file" | "folder">(null)
  const [dialogName, setDialogName] = useState("")
  const dirtyRef = useRef(dirty)
  dirtyRef.current = dirty

  useSubPageBreadcrumb(skill?.name ?? "Skill")

  const tree = useMemo(() => buildTree(files ?? []), [files])
  const editorExtensions = useMemo(() => (selectedPath ? languageFor(selectedPath) : []), [selectedPath])

  // Auto-open SKILL.md when the file list first arrives and nothing is selected.
  useEffect(() => {
    if (!files || selectedPath) return
    const first = files.find((f) => f.path === "SKILL.md") ?? files.find((f) => !f.directory)
    if (first) void openFile(first.path)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [files])

  const reloadFiles = () => {
    if (id) queryClient.invalidateQueries({ queryKey: queryKeys.skills.files(id) })
  }

  const openFile = async (path: string) => {
    if (!id) return
    if (dirtyRef.current && !window.confirm("Discard unsaved changes?")) return
    setLoadingFile(true)
    try {
      const file = await service.readFile(id, path)
      setSelectedPath(path)
      setContent(file.content)
      setDirty(false)
    } catch {
      toast.error("Could not open file.")
    } finally {
      setLoadingFile(false)
    }
  }

  const handleSave = async () => {
    if (!id || !selectedPath) return
    setSaving(true)
    try {
      await service.writeFile(id, selectedPath, content)
      setDirty(false)
      toast.success("Saved.")
      // SKILL.md edits change the indexed name/description.
      if (selectedPath === "SKILL.md") {
        queryClient.invalidateQueries({ queryKey: queryKeys.skills.all() })
      }
    } catch {
      toast.error("Save failed.")
    } finally {
      setSaving(false)
    }
  }

  const handleExport = async () => {
    if (!id) return
    try {
      await service.download(id, skill?.name ?? "skill")
    } catch {
      toast.error("Export failed.")
    }
  }

  const handleCreate = async () => {
    if (!id || !dialog) return
    const raw = dialogName.trim()
    if (!raw) return
    try {
      if (dialog === "folder") {
        await service.createFolder(id, raw)
        toast.success(`Folder "${raw}" created.`)
      } else {
        await service.writeFile(id, raw, "")
        toast.success(`File "${raw}" created.`)
      }
      setDialog(null)
      setDialogName("")
      reloadFiles()
      if (dialog === "file") {
        setSelectedPath(raw)
        setContent("")
        setDirty(false)
      }
    } catch {
      toast.error("Could not create. Check the name (no leading slash, no '..').")
    }
  }

  const handleDelete = async (path: string) => {
    if (!id) return
    if (!window.confirm(`Delete "${path}"? This cannot be undone.`)) return
    try {
      await service.deletePath(id, path)
      toast.success("Deleted.")
      if (selectedPath === path || selectedPath?.startsWith(path + "/")) {
        setSelectedPath(null)
        setContent("")
        setDirty(false)
      }
      reloadFiles()
    } catch {
      toast.error("Delete failed.")
    }
  }

  const toggleFolder = (path: string) =>
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(path)) next.delete(path)
      else next.add(path)
      return next
    })

  const renderNodes = (nodes: TreeNode[], depth: number) =>
    nodes.map((node) => (
      <div key={node.path}>
        <div
          className={`group flex items-center gap-1.5 rounded px-1.5 py-1 text-sm cursor-pointer hover:bg-accent ${
            selectedPath === node.path ? "bg-accent" : ""
          }`}
          style={{ paddingLeft: `${depth * 12 + 6}px` }}
          onClick={() => (node.directory ? toggleFolder(node.path) : openFile(node.path))}
        >
          {node.directory ? (
            <>
              {expanded.has(node.path) ? (
                <IconChevronDown className="size-3.5 shrink-0 text-muted-foreground" />
              ) : (
                <IconChevronRight className="size-3.5 shrink-0 text-muted-foreground" />
              )}
              <IconFolder className="size-4 shrink-0 text-blue-500" />
            </>
          ) : (
            <>
              <span className="size-3.5 shrink-0" />
              <IconFile className="size-4 shrink-0 text-muted-foreground" />
            </>
          )}
          <span className="truncate flex-1">{node.name}</span>
          <button
            type="button"
            title="Delete"
            className="p-0.5 rounded opacity-0 group-hover:opacity-100 hover:bg-destructive/10 text-muted-foreground hover:text-destructive"
            onClick={(e) => { e.stopPropagation(); handleDelete(node.path) }}
          >
            <IconTrash className="size-3.5" />
          </button>
        </div>
        {node.directory && expanded.has(node.path) && renderNodes(node.children, depth + 1)}
      </div>
    ))

  return (
    <div className={containerClassName}>
      {/* Header */}
      <div className="flex items-center gap-3 border-b px-4 lg:px-6 py-3">
        <Button variant="ghost" size="sm" onClick={() => navigate(baseRoute)}>
          <IconArrowLeft className="size-4! mr-1.5" />
          Skills
        </Button>
        <span className="flex size-7 items-center justify-center rounded-md bg-gradient-to-br from-blue-600 to-indigo-600 text-white">
          <IconSparkles className="size-4" />
        </span>
        <div className="min-w-0">
          <div className="font-semibold truncate">{skill?.name ?? "…"}</div>
          {skill?.version && <div className="text-xs text-muted-foreground">v{skill.version}</div>}
        </div>
        <div className="ml-auto flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={handleExport} disabled={!skill}>
            <IconDownload className="size-4! mr-1.5" />
            Export
          </Button>
          <GradientButton size="sm" onClick={handleSave} disabled={!selectedPath || !dirty || saving}>
            {saving ? <IconLoader2 className="size-4! mr-1.5 animate-spin" /> : <IconDeviceFloppy className="size-4! mr-1.5" />}
            Save
          </GradientButton>
        </div>
      </div>

      {/* Body: file tree + editor */}
      <ResizablePanelGroup orientation="horizontal" className="flex-1">
        <ResizablePanel defaultSize="25" minSize="15" overflowHidden>
          <div className="flex h-full flex-col">
            {/* Tree toolbar — the skill name is the root; there is no way above it */}
            <div className="flex items-center gap-1 border-b px-2 py-1.5">
              <span className="flex-1 truncate px-1 text-xs font-medium text-muted-foreground uppercase tracking-wide">
                {skill?.name ?? "Files"}
              </span>
              <button
                type="button"
                title="New file"
                className="p-1 rounded hover:bg-accent text-muted-foreground hover:text-foreground"
                onClick={() => { setDialog("file"); setDialogName("") }}
              >
                <IconFilePlus className="size-4" />
              </button>
              <button
                type="button"
                title="New folder"
                className="p-1 rounded hover:bg-accent text-muted-foreground hover:text-foreground"
                onClick={() => { setDialog("folder"); setDialogName("") }}
              >
                <IconFolderPlus className="size-4" />
              </button>
            </div>
            <div className="flex-1 overflow-auto p-1">
              {files && files.length === 0 ? (
                <p className="px-2 py-3 text-xs text-muted-foreground">Empty skill folder.</p>
              ) : (
                renderNodes(tree, 0)
              )}
            </div>
          </div>
        </ResizablePanel>

        <ResizableHandle withHandle />

        <ResizablePanel defaultSize="75" minSize="40" overflowHidden>
          <div className="flex h-full flex-col">
            {selectedPath ? (
              <>
                <div className="flex items-center gap-2 border-b px-3 py-1.5 text-sm text-muted-foreground">
                  <IconFile className="size-4" />
                  <span className="truncate">{selectedPath}</span>
                  {dirty && <span className="size-2 rounded-full bg-amber-500" title="Unsaved changes" />}
                </div>
                <div className="flex-1 overflow-auto">
                  {loadingFile ? (
                    <div className="flex h-full items-center justify-center text-muted-foreground">
                      <IconLoader2 className="size-5 animate-spin" />
                    </div>
                  ) : (
                    <CodeMirror
                      value={content}
                      height="100%"
                      theme={oneDark}
                      extensions={editorExtensions}
                      onChange={(val) => { setContent(val); setDirty(true) }}
                      basicSetup={{ lineNumbers: true, foldGutter: true, highlightActiveLine: true }}
                      style={{ height: "100%" }}
                    />
                  )}
                </div>
              </>
            ) : (
              <div className="flex h-full flex-col items-center justify-center text-muted-foreground">
                <IconFile className="size-10 mb-3 opacity-40" />
                <p className="text-sm">Select a file to edit.</p>
              </div>
            )}
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>

      {/* New file / folder dialog */}
      <Dialog open={dialog !== null} onOpenChange={(open) => { if (!open) setDialog(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{dialog === "folder" ? "New Folder" : "New File"}</DialogTitle>
          </DialogHeader>
          <Input
            placeholder={dialog === "folder" ? "scripts" : "scripts/build.py"}
            value={dialogName}
            onChange={(e) => setDialogName(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") handleCreate() }}
            autoFocus
          />
          <p className="text-xs text-muted-foreground">
            Path is relative to the skill root. Subfolders are created automatically.
          </p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialog(null)}>Cancel</Button>
            <GradientButton onClick={handleCreate} disabled={!dialogName.trim()}>Create</GradientButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
