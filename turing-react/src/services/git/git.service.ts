import axios from "axios"
import type { TurGitRepository } from "@/models/git/git-repository.model"
import type { TurGitBuildStatus } from "@/models/git/git-build-status.model"

export class TurGitService {
  async list(): Promise<TurGitRepository[]> {
    const response = await axios.get<TurGitRepository[]>("/api/git/repository")
    return response.data
  }

  async create(name: string): Promise<TurGitRepository> {
    const response = await axios.post<TurGitRepository>("/api/git/repository", { name })
    return response.data
  }

  async delete(name: string): Promise<void> {
    await axios.delete(`/api/git/repository/${name}`)
  }

  async startBuild(name: string): Promise<TurGitBuildStatus> {
    const response = await axios.post<TurGitBuildStatus>(`/api/git/repository/${name}/build`)
    return response.data
  }

  async getBuildStatus(name: string): Promise<TurGitBuildStatus> {
    const response = await axios.get<TurGitBuildStatus>(`/api/git/repository/${name}/build/status`)
    return response.data
  }
}
