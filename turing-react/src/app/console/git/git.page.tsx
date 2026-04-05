import { ROUTES } from "@/app/routes.const"
import { LoadProvider } from "@/components/loading-provider"
import { SubPageHeader } from "@/components/sub.page.header"
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import type { TurGitRepository } from "@/models/git/git-repository.model"
import { TurGitService } from "@/services/git/git.service"
import {
  IconCopy,
  IconGitBranch,
  IconInfoCircle,
  IconPlus,
  IconTrash,
} from "@tabler/icons-react"
import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"
import { useTranslation } from "react-i18next"

const turGitService = new TurGitService()

function getCloneUrls(name: string): { https: string; ssh: string } {
  const baseUrl = `${window.location.protocol}//${window.location.host}`
  return {
    https: `${baseUrl}/git/${name}.git`,
    ssh: `git@${window.location.hostname}:${name}.git`,
  }
}

export default function GitPage() {
  const { t } = useTranslation()
  const [repositories, setRepositories] = useState<TurGitRepository[] | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [newRepoName, setNewRepoName] = useState("")
  const [infoRepo, setInfoRepo] = useState<TurGitRepository | null>(null)
  useSubPageBreadcrumb(t("git.title"))

  const loadRepositories = useCallback(() => {
    setError(null)
    setRepositories(undefined)
    turGitService.list()
      .then(setRepositories)
      .catch(() => setError(t("git.loadError")))
  }, [t])

  useEffect(() => { loadRepositories() }, [loadRepositories])

  const handleCreate = async () => {
    const name = newRepoName.trim()
    if (!name) return
    try {
      await turGitService.create(name)
      toast.success(t("git.createSuccess", { name }))
      setCreateDialogOpen(false)
      setNewRepoName("")
      loadRepositories()
    } catch {
      toast.error(t("git.createError"))
    }
  }

  const handleDelete = async (name: string) => {
    try {
      await turGitService.delete(name)
      toast.success(t("git.deleteSuccess", { name }))
      if (infoRepo?.name === name) setInfoRepo(null)
      loadRepositories()
    } catch {
      toast.error(t("git.deleteError"))
    }
  }

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text).then(() => toast.success(t("git.copied")))
  }

  return (
    <LoadProvider checkIsNotUndefined={repositories} error={error} tryAgainUrl={ROUTES.GIT_ROOT}>
      <SubPageHeader
        icon={IconGitBranch}
        feature={t("git.title")}
        name={t("git.title")}
        description={t("git.description")}
      />

      <div className="px-4 lg:px-6 space-y-4">
        {/* Toolbar */}
        <div className="flex items-center gap-3">
          <GradientButton size="sm" onClick={() => { setNewRepoName(""); setCreateDialogOpen(true) }}>
            <IconPlus className="size-4! mr-1.5" />
            {t("git.newRepository")}
          </GradientButton>
        </div>

        {/* Repository list */}
        {repositories && repositories.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
            <IconGitBranch className="size-12 mb-3 opacity-40" />
            <p className="text-sm">{t("git.noRepositories")}</p>
          </div>
        ) : (
          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("git.columns.name")}</TableHead>
                  <TableHead>{t("git.columns.directory")}</TableHead>
                  <TableHead className="w-[120px] text-right">{t("git.columns.actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {repositories?.map((repo) => (
                  <TableRow key={repo.name}>
                    <TableCell className="font-medium">
                      <div className="flex items-center gap-2">
                        <IconGitBranch className="size-4 text-blue-500 shrink-0" />
                        <span>{repo.name}</span>
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground text-sm font-mono">{repo.dirName}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button
                          type="button"
                          className="p-1.5 rounded-md hover:bg-accent text-muted-foreground hover:text-foreground transition-colors"
                          title={t("git.info")}
                          onClick={() => setInfoRepo(infoRepo?.name === repo.name ? null : repo)}
                        >
                          <IconInfoCircle className="size-4" />
                        </button>
                        <button
                          type="button"
                          className="p-1.5 rounded-md hover:bg-destructive/10 text-muted-foreground hover:text-destructive transition-colors"
                          title={t("git.delete")}
                          onClick={() => handleDelete(repo.name)}
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

        {/* Clone info panel */}
        {infoRepo && (
          <div className="rounded-lg border p-4 space-y-3 bg-muted/30">
            <p className="text-sm font-semibold">{t("git.cloneInfo.title", { name: infoRepo.name })}</p>

            <div className="space-y-2">
              <p className="text-xs text-muted-foreground font-medium uppercase tracking-wide">{t("git.cloneInfo.https")}</p>
              <div className="flex items-center gap-2">
                <code className="flex-1 text-xs bg-background border rounded px-3 py-2 font-mono truncate">
                  {getCloneUrls(infoRepo.name).https}
                </code>
                <button
                  type="button"
                  className="p-1.5 rounded-md hover:bg-accent text-muted-foreground hover:text-foreground transition-colors shrink-0"
                  onClick={() => copyToClipboard(getCloneUrls(infoRepo.name).https)}
                >
                  <IconCopy className="size-4" />
                </button>
              </div>
            </div>

            <div className="space-y-1 pt-1">
              <p className="text-xs text-muted-foreground font-medium">{t("git.cloneInfo.usage")}</p>
              <code className="block text-xs bg-background border rounded px-3 py-2 font-mono">
                {`git clone ${getCloneUrls(infoRepo.name).https}`}
              </code>
            </div>

            <div className="space-y-1 pt-1 text-xs text-muted-foreground">
              <p>{t("git.cloneInfo.authNote")}</p>
            </div>
          </div>
        )}
      </div>

      {/* Create Repository Dialog */}
      <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("git.createDialog.title")}</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Input
              placeholder={t("git.createDialog.placeholder")}
              value={newRepoName}
              onChange={(e) => setNewRepoName(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") handleCreate() }}
              autoFocus
            />
            <p className="text-xs text-muted-foreground">{t("git.createDialog.hint")}</p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateDialogOpen(false)}>
              {t("git.createDialog.cancel")}
            </Button>
            <GradientButton onClick={handleCreate} disabled={!newRepoName.trim()}>
              {t("git.createDialog.create")}
            </GradientButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </LoadProvider>
  )
}
