export type TurGitBuildState = "IDLE" | "RUNNING" | "COMPLETED" | "FAILED"

export interface TurGitBuildStatus {
  state: TurGitBuildState
  logLines: string[]
  siteName: string | null
  errorMessage: string | null
  startedAt: string | null
  completedAt: string | null
}
