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
import type { TurGitBuildStatus } from "@/models/git/git-build-status.model"
import type { TurGitRepository } from "@/models/git/git-repository.model"
import { TurGitService } from "@/services/git/git.service"
import {
  IconCheck,
  IconCopy,
  IconExternalLink,
  IconGitBranch,
  IconInfoCircle,
  IconLoader2,
  IconPlayerPlay,
  IconPlus,
  IconTrash,
  IconX,
} from "@tabler/icons-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { useTranslation } from "react-i18next"

const turGitService = new TurGitService()

function getCloneUrl(name: string): string {
  const baseUrl = `${window.location.protocol}//${window.location.host}`
  return `${baseUrl}/git/${name}.git`
}

function getPageUrl(siteName: string): string {
  return `/pages/${siteName}/index.html`
}

export default function GitPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [repositories, setRepositories] = useState<TurGitRepository[] | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [newRepoName, setNewRepoName] = useState("")
  const [infoRepo, setInfoRepo] = useState<TurGitRepository | null>(null)
  // Build pipeline state: keyed by repo name
  const [buildStatuses, setBuildStatuses] = useState<Record<string, TurGitBuildStatus>>({})
  const [buildLogRepo, setBuildLogRepo] = useState<string | null>(null)
  const pollingRefs = useRef<Record<string, ReturnType<typeof setInterval>>>({})
  const logEndRef = useRef<HTMLDivElement>(null)
  useSubPageBreadcrumb(t("git.title"))

  const loadRepositories = useCallback(() => {
    setError(null)
    setRepositories(undefined)
    turGitService.list()
      .then(setRepositories)
      .catch(() => setError(t("git.loadError")))
  }, [t])

  useEffect(() => { loadRepositories() }, [loadRepositories])

  // Auto-scroll log to bottom when new lines arrive
  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [buildStatuses, buildLogRepo])

  // Stop all polling on unmount
  useEffect(() => {
    const refs = pollingRefs.current
    return () => { Object.values(refs).forEach(clearInterval) }
  }, [])

  const stopPolling = (name: string) => {
    if (pollingRefs.current[name]) {
      clearInterval(pollingRefs.current[name])
      delete pollingRefs.current[name]
    }
  }

  const startPolling = useCallback((name: string) => {
    stopPolling(name)
    pollingRefs.current[name] = setInterval(async () => {
      try {
        const status = await turGitService.getBuildStatus(name)
        setBuildStatuses(prev => ({ ...prev, [name]: status }))
        if (status.state !== "RUNNING") {
          stopPolling(name)
          if (status.state === "COMPLETED") {
            toast.success(t("git.pipeline.deployedSuccess", { name: status.siteName }))
          } else if (status.state === "FAILED") {
            toast.error(t("git.pipeline.buildFailed"))
          }
        }
      } catch { /* ignore polling errors */ }
    }, 2000)
  }, [t])

  const handleBuild = async (repo: TurGitRepository) => {
    const existing = buildStatuses[repo.name]
    if (existing?.state === "RUNNING") return

    try {
      const status = await turGitService.startBuild(repo.name)
      setBuildStatuses(prev => ({ ...prev, [repo.name]: status }))
      setBuildLogRepo(repo.name)
      startPolling(repo.name)
    } catch {
      toast.error(t("git.pipeline.startFailed"))
    }
  }

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
      stopPolling(name)
      loadRepositories()
    } catch {
      toast.error(t("git.deleteError"))
    }
  }

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text).then(() => toast.success(t("git.copied")))
  }

  const buildStatusOf = (name: string): TurGitBuildStatus | null => buildStatuses[name] ?? null

  const isBuildRunning = (name: string) => buildStatusOf(name)?.state === "RUNNING"

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
                  <TableHead>{t("git.columns.buildStatus")}</TableHead>
                  <TableHead className="w-[140px] text-right">{t("git.columns.actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {repositories?.map((repo) => {
                  const bs = buildStatusOf(repo.name)
                  const running = isBuildRunning(repo.name)
                  return (
                    <TableRow key={repo.name}>
                      <TableCell className="font-medium">
                        <button
                          type="button"
                          className="flex items-center gap-2 hover:text-primary transition-colors cursor-pointer"
                          onClick={() => navigate(`${ROUTES.GIT_ROOT}/${repo.name}`)}
                        >
                          <IconGitBranch className="size-4 text-blue-500 shrink-0" />
                          <span className="hover:underline">{repo.name}</span>
                        </button>
                      </TableCell>
                      <TableCell className="text-muted-foreground text-sm font-mono">{repo.dirName}</TableCell>
                      <TableCell>
                        {bs ? (
                          <div className="flex items-center gap-1.5 text-xs">
                            {bs.state === "RUNNING" && (
                              <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400">
                                <IconLoader2 className="size-3.5 animate-spin" />
                                {t("git.pipeline.running")}
                              </span>
                            )}
                            {bs.state === "COMPLETED" && (
                              <span className="flex items-center gap-1 text-emerald-600 dark:text-emerald-400">
                                <IconCheck className="size-3.5" />
                                <a
                                  href={getPageUrl(bs.siteName!)}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="hover:underline flex items-center gap-1"
                                  onClick={(e) => e.stopPropagation()}
                                >
                                  {t("git.pipeline.deployed", { name: bs.siteName })}
                                  <IconExternalLink className="size-3" />
                                </a>
                              </span>
                            )}
                            {bs.state === "FAILED" && (
                              <span className="flex items-center gap-1 text-red-600 dark:text-red-400">
                                <IconX className="size-3.5" />
                                {t("git.pipeline.failed")}
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className="text-xs text-muted-foreground/50">{t("git.pipeline.idle")}</span>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          {/* Build / Pipeline button */}
                          <button
                            type="button"
                            disabled={running}
                            className={`p-1.5 rounded-md transition-colors ${
                              running
                                ? "text-muted-foreground/40 cursor-not-allowed"
                                : "hover:bg-emerald-500/10 text-muted-foreground hover:text-emerald-600 dark:hover:text-emerald-400"
                            }`}
                            title={running ? t("git.pipeline.running") : t("git.pipeline.run")}
                            onClick={() => { if (!running) { handleBuild(repo); } }}
                          >
                            {running
                              ? <IconLoader2 className="size-4 animate-spin" />
                              : <IconPlayerPlay className="size-4" />
                            }
                          </button>
                          {/* Build log button (only when status exists) */}
                          {bs && bs.state !== "IDLE" && (
                            <button
                              type="button"
                              className="p-1.5 rounded-md hover:bg-accent text-muted-foreground hover:text-foreground transition-colors"
                              title={t("git.pipeline.viewLogs")}
                              onClick={() => setBuildLogRepo(buildLogRepo === repo.name ? null : repo.name)}
                            >
                              <IconInfoCircle className="size-4" />
                            </button>
                          )}
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
                  )
                })}
              </TableBody>
            </Table>
          </div>
        )}

        {/* Build log panel */}
        {buildLogRepo && buildStatuses[buildLogRepo] && (
          <div className="rounded-lg border overflow-hidden">
            <div className="flex items-center justify-between px-3 py-2 bg-muted/50 border-b">
              <span className="text-xs font-mono font-semibold text-foreground">
                {t("git.pipeline.logsTitle", { name: buildLogRepo })}
              </span>
              <button
                type="button"
                className="text-muted-foreground hover:text-foreground transition-colors"
                onClick={() => setBuildLogRepo(null)}
              >
                <IconX className="size-3.5" />
              </button>
            </div>
            <div className="h-64 overflow-y-auto">
              <div className="p-3 font-mono text-xs space-y-0.5 bg-black/90 dark:bg-black/95 text-green-400 dark:text-green-300 min-h-64">
                {buildStatuses[buildLogRepo].logLines.map((line, i) => (
                  <div key={i} className={
                    line.includes("FAILED") || line.includes("Error") || line.includes("error")
                      ? "text-red-400"
                      : line.includes("===") ? "text-cyan-300 font-bold"
                      : ""
                  }>
                    {line}
                  </div>
                ))}
                {buildStatuses[buildLogRepo].state === "RUNNING" && (
                  <div className="flex items-center gap-1 text-amber-400">
                    <IconLoader2 className="size-3 animate-spin" />
                    <span>{t("git.pipeline.running")}...</span>
                  </div>
                )}
                <div ref={logEndRef} />
              </div>
            </div>
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
                  {getCloneUrl(infoRepo.name)}
                </code>
                <button
                  type="button"
                  className="p-1.5 rounded-md hover:bg-accent text-muted-foreground hover:text-foreground transition-colors shrink-0"
                  onClick={() => copyToClipboard(getCloneUrl(infoRepo.name))}
                >
                  <IconCopy className="size-4" />
                </button>
              </div>
            </div>

            <div className="space-y-1 pt-1">
              <p className="text-xs text-muted-foreground font-medium">{t("git.cloneInfo.usage")}</p>
              <code className="block text-xs bg-background border rounded px-3 py-2 font-mono">
                {`git clone ${getCloneUrl(infoRepo.name)}`}
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
