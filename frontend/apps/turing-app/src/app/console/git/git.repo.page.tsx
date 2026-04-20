import { SubPageHeader } from "@/components/sub.page.header"
import { Button } from "@/components/ui/button"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  TurGitService,
  type TurGitBranch,
  type TurGitTreeEntry,
} from "@/services/git/git.service"
import {
  IconFile,
  IconFolder,
  IconGitBranch,
  IconArrowLeft,
  IconChevronRight,
  IconLoader2,
} from "@tabler/icons-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router-dom"

const turGitService = new TurGitService()

function formatSize(bytes: number): string {
  if (bytes === 0) return "-"
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default function GitRepoPage() {
  const { t } = useTranslation()
  const { name } = useParams<{ name: string }>()

  const [branches, setBranches] = useState<TurGitBranch[]>([])
  const [currentBranch, setCurrentBranch] = useState<string>("")
  const [entries, setEntries] = useState<TurGitTreeEntry[]>([])
  const [currentPath, setCurrentPath] = useState("")
  const [loading, setLoading] = useState(true)
  const [fileContent, setFileContent] = useState<string | null>(null)
  const [fileName, setFileName] = useState<string | null>(null)

  const loadBranches = useCallback(async () => {
    if (!name) return
    try {
      const branchList = await turGitService.listBranches(name)
      setBranches(branchList)
      if (branchList.length === 0) {
        // Empty repo — no branches, no commits
        setLoading(false)
        return
      }
      const defaultBranch = branchList.find((b) => b.isDefault)
      setCurrentBranch(defaultBranch?.name || branchList[0]?.name || "")
    } catch {
      setBranches([])
      setLoading(false)
    }
  }, [name])

  const loadTree = useCallback(async () => {
    if (!name || !currentBranch) return
    setLoading(true)
    setFileContent(null)
    setFileName(null)
    try {
      const tree = await turGitService.listTree(name, currentBranch, currentPath || undefined)
      setEntries(tree)
    } catch {
      setEntries([])
    } finally {
      setLoading(false)
    }
  }, [name, currentBranch, currentPath])

  useEffect(() => {
    loadBranches()
  }, [loadBranches])

  useEffect(() => {
    if (currentBranch) loadTree()
  }, [currentBranch, currentPath, loadTree])

  const handleEntryClick = async (entry: TurGitTreeEntry) => {
    if (entry.type === "tree") {
      setCurrentPath(entry.path)
    } else {
      // Load file content
      if (!name) return
      try {
        const content = await turGitService.readFile(name, entry.path, currentBranch)
        setFileContent(content)
        setFileName(entry.name)
      } catch {
        setFileContent("Failed to load file content.")
        setFileName(entry.name)
      }
    }
  }

  const navigateUp = () => {
    if (fileContent !== null) {
      setFileContent(null)
      setFileName(null)
      return
    }
    const parts = currentPath.split("/").filter(Boolean)
    parts.pop()
    setCurrentPath(parts.join("/"))
  }

  const pathParts = currentPath.split("/").filter(Boolean)
  const isRoot = currentPath === "" && fileContent === null

  return (
    <>
      <SubPageHeader
        icon={IconGitBranch}
        feature={t("git.title")}
        name={name || ""}
        description={t("git.browser.description")}
      />

      <div className="px-4 lg:px-6 space-y-4">
        {/* Toolbar: branch selector + breadcrumb */}
        <div className="flex items-center gap-3 flex-wrap">
          {branches.length > 0 && (
            <Select value={currentBranch} onValueChange={(v) => { setCurrentBranch(v); setCurrentPath(""); setFileContent(null); }}>
              <SelectTrigger className="w-48">
                <IconGitBranch className="size-4 mr-1.5 text-muted-foreground" />
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {branches.map((b) => (
                  <SelectItem key={b.name} value={b.name}>{b.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}

          {/* Breadcrumb */}
          <div className="flex items-center gap-1 text-sm text-muted-foreground">
            <button
              type="button"
              className="hover:text-foreground transition-colors cursor-pointer font-medium"
              onClick={() => { setCurrentPath(""); setFileContent(null); setFileName(null); }}
            >
              {name}
            </button>
            {pathParts.map((part, i) => (
              <span key={i} className="flex items-center gap-1">
                <IconChevronRight className="size-3.5" />
                <button
                  type="button"
                  className="hover:text-foreground transition-colors cursor-pointer"
                  onClick={() => {
                    setCurrentPath(pathParts.slice(0, i + 1).join("/"))
                    setFileContent(null)
                    setFileName(null)
                  }}
                >
                  {part}
                </button>
              </span>
            ))}
            {fileName && (
              <span className="flex items-center gap-1">
                <IconChevronRight className="size-3.5" />
                <span className="text-foreground font-medium">{fileName}</span>
              </span>
            )}
          </div>
        </div>

        {/* Back button */}
        {!isRoot && (
          <Button variant="ghost" size="sm" onClick={navigateUp} className="gap-1.5">
            <IconArrowLeft className="size-4" />
            {t("git.browser.back")}
          </Button>
        )}

        {loading ? (
          <div className="flex items-center justify-center py-16">
            <IconLoader2 className="size-8 text-muted-foreground animate-spin" />
          </div>
        ) : fileContent !== null ? (
          /* File viewer */
          <div className="rounded-lg border overflow-hidden">
            <div className="px-4 py-2 bg-muted/50 border-b text-xs font-mono text-muted-foreground">
              {fileName}
            </div>
            <pre className="p-4 text-sm font-mono overflow-x-auto bg-background max-h-[70vh] overflow-y-auto whitespace-pre">
              {fileContent}
            </pre>
          </div>
        ) : entries.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
            <IconFolder className="size-12 mb-3 opacity-40" />
            <p className="text-sm">{t("git.browser.empty")}</p>
          </div>
        ) : (
          /* File tree table */
          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("git.browser.name")}</TableHead>
                  <TableHead className="w-24 text-right">{t("git.browser.size")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {entries.map((entry) => (
                  <TableRow
                    key={entry.path}
                    className="cursor-pointer hover:bg-accent/50"
                    onClick={() => handleEntryClick(entry)}
                  >
                    <TableCell>
                      <div className="flex items-center gap-2">
                        {entry.type === "tree" ? (
                          <IconFolder className="size-4 text-blue-500 shrink-0" />
                        ) : (
                          <IconFile className="size-4 text-muted-foreground shrink-0" />
                        )}
                        <span className={entry.type === "tree" ? "font-medium" : ""}>
                          {entry.name}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell className="text-right text-muted-foreground text-sm font-mono">
                      {entry.type === "blob" ? formatSize(entry.size) : ""}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </div>
    </>
  )
}
