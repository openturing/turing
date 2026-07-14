import { ROUTES } from "@/app/routes.const"
import { BentoHero } from "@/components/bento"
import { LoadProvider } from "@/components/loading-provider"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { GradientButton } from "@/components/ui/gradient-button"
import { Input } from "@/components/ui/input"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
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
import { toast } from "@viglet/viglet-design-system"
import { useCallback, useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

const turGitService = new TurGitService()

function getCloneUrl(name: string): string {
  return `${window.location.protocol}//${window.location.host}/git/${name}.git`
}
function getPageUrl(siteName: string): string {
  return `/pages/${siteName}/index.html`
}

/**
 * Bento Git — T556. Repository management + build pipeline restyled onto the
 * frosted Bento shell (BentoHero + glass card). Logic is unchanged from the
 * console surface; only the chrome is Bento.
 */
export default function BentoGitPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [repositories, setRepositories] = useState<TurGitRepository[] | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [newRepoName, setNewRepoName] = useState("")
  const [infoRepo, setInfoRepo] = useState<TurGitRepository | null>(null)
  const [buildStatuses, setBuildStatuses] = useState<Record<string, TurGitBuildStatus>>({})
  const [buildLogRepo, setBuildLogRepo] = useState<string | null>(null)
  const pollingRefs = useRef<Record<string, ReturnType<typeof setInterval>>>({})
  const logEndRef = useRef<HTMLDivElement>(null)

  const loadRepositories = useCallback(() => {
    setError(null)
    setRepositories(undefined)
    turGitService.list().then(setRepositories).catch(() => setError(t("git.loadError")))
  }, [t])

  useEffect(() => { loadRepositories() }, [loadRepositories])

  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [buildStatuses, buildLogRepo])

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
        setBuildStatuses((prev) => ({ ...prev, [name]: status }))
        if (status.state !== "RUNNING") {
          stopPolling(name)
          if (status.state === "COMPLETED") toast.success(t("git.pipeline.deployedSuccess", { name: status.siteName }))
          else if (status.state === "FAILED") toast.error(t("git.pipeline.buildFailed"))
        }
      } catch { /* ignore polling errors */ }
    }, 2000)
  }, [t])

  const handleBuild = async (repo: TurGitRepository) => {
    if (buildStatuses[repo.name]?.state === "RUNNING") return
    try {
      const status = await turGitService.startBuild(repo.name)
      setBuildStatuses((prev) => ({ ...prev, [repo.name]: status }))
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
    <LoadProvider checkIsNotUndefined={repositories} error={error} tryAgainUrl={ROUTES.BENTO_GIT}>
      <BentoHero
        backTo={ROUTES.BENTO_AREA_MANAGEMENT}
        backLabel={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-violet-500 to-purple-600 text-white shadow-md">
            <IconGitBranch size={24} />
          </span>
        }
        title={t("git.title")}
        subtitle={t("git.noRepositories")}
        trailing={
          <GradientButton size="sm" onClick={() => { setNewRepoName(""); setCreateDialogOpen(true) }}>
            <IconPlus className="mr-1.5 size-4!" />
            {t("git.newRepository")}
          </GradientButton>
        }
      />

      <div className="space-y-4">
        {repositories && repositories.length === 0 ? (
          <div className="bento-glass flex flex-col items-center justify-center rounded-3xl py-16 text-muted-foreground">
            <IconGitBranch className="mb-3 size-12 opacity-40" />
            <p className="text-sm">{t("git.noRepositories")}</p>
          </div>
        ) : (
          <div className="bento-glass overflow-hidden rounded-3xl">
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
                          className="flex cursor-pointer items-center gap-2 transition-colors hover:text-primary"
                          onClick={() => navigate(`${ROUTES.BENTO_GIT}/${repo.name}`)}
                        >
                          <IconGitBranch className="size-4 shrink-0 text-violet-500" />
                          <span className="hover:underline">{repo.name}</span>
                        </button>
                      </TableCell>
                      <TableCell className="font-mono text-sm text-muted-foreground">{repo.dirName}</TableCell>
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
                                <a href={getPageUrl(bs.siteName!)} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1 hover:underline" onClick={(e) => e.stopPropagation()}>
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
                          <button
                            type="button"
                            disabled={running}
                            className={`rounded-md p-1.5 transition-colors ${running ? "cursor-not-allowed text-muted-foreground/40" : "text-muted-foreground hover:bg-emerald-500/10 hover:text-emerald-600 dark:hover:text-emerald-400"}`}
                            title={running ? t("git.pipeline.running") : t("git.pipeline.run")}
                            onClick={() => { if (!running) handleBuild(repo) }}
                          >
                            {running ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlayerPlay className="size-4" />}
                          </button>
                          {bs && bs.state !== "IDLE" && (
                            <button
                              type="button"
                              className="rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
                              title={t("git.pipeline.viewLogs")}
                              onClick={() => setBuildLogRepo(buildLogRepo === repo.name ? null : repo.name)}
                            >
                              <IconInfoCircle className="size-4" />
                            </button>
                          )}
                          <button
                            type="button"
                            className="rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
                            title={t("git.info")}
                            onClick={() => setInfoRepo(infoRepo?.name === repo.name ? null : repo)}
                          >
                            <IconInfoCircle className="size-4" />
                          </button>
                          <button
                            type="button"
                            className="rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive"
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

        {buildLogRepo && buildStatuses[buildLogRepo] && (
          <div className="bento-glass overflow-hidden rounded-3xl">
            <div className="flex items-center justify-between border-b bg-muted/50 px-3 py-2">
              <span className="font-mono text-xs font-semibold text-foreground">{t("git.pipeline.logsTitle", { name: buildLogRepo })}</span>
              <button type="button" className="text-muted-foreground transition-colors hover:text-foreground" onClick={() => setBuildLogRepo(null)}>
                <IconX className="size-3.5" />
              </button>
            </div>
            <div className="h-64 overflow-y-auto">
              <div className="min-h-64 space-y-0.5 bg-black/90 p-3 font-mono text-xs text-green-400 dark:bg-black/95 dark:text-green-300">
                {buildStatuses[buildLogRepo].logLines.map((line, i) => (
                  <div key={i} className={line.includes("FAILED") || line.includes("Error") || line.includes("error") ? "text-red-400" : line.includes("===") ? "font-bold text-cyan-300" : ""}>
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

        {infoRepo && (
          <div className="bento-glass space-y-3 rounded-3xl p-4">
            <p className="text-sm font-semibold">{t("git.cloneInfo.title", { name: infoRepo.name })}</p>
            <div className="space-y-2">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{t("git.cloneInfo.https")}</p>
              <div className="flex items-center gap-2">
                <code className="flex-1 truncate rounded border bg-background px-3 py-2 font-mono text-xs">{getCloneUrl(infoRepo.name)}</code>
                <button type="button" className="shrink-0 rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground" onClick={() => copyToClipboard(getCloneUrl(infoRepo.name))}>
                  <IconCopy className="size-4" />
                </button>
              </div>
            </div>
            <div className="space-y-1 pt-1">
              <p className="text-xs font-medium text-muted-foreground">{t("git.cloneInfo.usage")}</p>
              <code className="block rounded border bg-background px-3 py-2 font-mono text-xs">{`git clone ${getCloneUrl(infoRepo.name)}`}</code>
            </div>
            <div className="space-y-1 pt-1 text-xs text-muted-foreground">
              <p>{t("git.cloneInfo.authNote")}</p>
            </div>
          </div>
        )}
      </div>

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
            <Button variant="outline" onClick={() => setCreateDialogOpen(false)}>{t("git.createDialog.cancel")}</Button>
            <GradientButton onClick={handleCreate} disabled={!newRepoName.trim()}>{t("git.createDialog.create")}</GradientButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </LoadProvider>
  )
}
