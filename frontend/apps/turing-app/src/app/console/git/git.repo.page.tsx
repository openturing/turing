import { ROUTES } from "@/app/routes.const"
import { BentoHero, bentoChipClass } from "@/components/bento"
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
import { useGitBranches, useGitTree } from "@/api/queries/git.queries"
import { TurGitService, type TurGitTreeEntry } from "@/services/git/git.service"
import {
  IconFile,
  IconFolder,
  IconGitBranch,
  IconArrowLeft,
  IconChevronRight,
  IconLoader2,
} from "@tabler/icons-react"
import { useState } from "react"
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

  // T1018 — branches and the tree both come from the query cache. The branch a
  // page shows is derived: an unpicked one is the repository's default, or its
  // first if none is marked. The tree query is keyed by branch and path, so
  // walking back up a directory is served from cache instead of re-listed.
  const { data: branches = [], isLoading: branchesLoading } = useGitBranches(name)
  const [pickedBranch, setPickedBranch] = useState<string>("")
  const defaultBranch = branches.find((b) => b.isDefault)?.name ?? branches[0]?.name ?? ""
  const currentBranch = pickedBranch || defaultBranch
  const [currentPath, setCurrentPath] = useState("")
  const { data: entries = [], isLoading: treeLoading } = useGitTree(name, currentBranch, currentPath)
  // An empty repository has no branches and therefore nothing to list, so it
  // must not sit on the spinner the tree query never starts.
  const loading = branchesLoading || (branches.length > 0 && treeLoading)
  const [fileContent, setFileContent] = useState<string | null>(null)
  const [fileName, setFileName] = useState<string | null>(null)

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
      <BentoHero
        backTo={ROUTES.BENTO_GIT}
        backLabel={t("git.title")}
        leading={
          <span className={`grid h-12 w-12 place-items-center rounded-2xl ${bentoChipClass("violet")} text-white shadow-md`}>
            <IconGitBranch size={24} />
          </span>
        }
        title={name || ""}
        subtitle={t("git.browser.description")}
      />

      <div className="px-4 lg:px-6 space-y-4">
        {/* Toolbar: branch selector + breadcrumb */}
        <div className="flex items-center gap-3 flex-wrap">
          {branches.length > 0 && (
            <Select value={currentBranch} onValueChange={(v) => { setPickedBranch(v); setCurrentPath(""); setFileContent(null); }}>
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
