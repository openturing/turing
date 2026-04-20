export interface TurAssetTrainingStatus {
  state: "IDLE" | "RUNNING" | "COMPLETED" | "FAILED"
  totalCount: number
  processedCount: number
  errorCount: number
  startedAt: string
  completedAt: string
  errorMessage: string
}
