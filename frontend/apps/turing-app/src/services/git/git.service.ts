import axios from "axios"
import type { TurGitRepository } from "@/models/git/git-repository.model"
import type { TurGitBuildStatus } from "@/models/git/git-build-status.model"

export interface TurGitBranch {
  name: string
  isDefault: boolean
}

export interface TurGitTreeEntry {
  name: string
  path: string
  type: "tree" | "blob"
  size: number
}

export class TurGitService {
  async list(): Promise<TurGitRepository[]> {
    const response = await axios.get<TurGitRepository[]>("/git/repository")
    return response.data
  }

  async create(name: string): Promise<TurGitRepository> {
    const response = await axios.post<TurGitRepository>("/git/repository", { name })
    return response.data
  }

  async delete(name: string): Promise<void> {
    await axios.delete(`/git/repository/${name}`)
  }

  async startBuild(name: string): Promise<TurGitBuildStatus> {
    const response = await axios.post<TurGitBuildStatus>(`/git/repository/${name}/build`)
    return response.data
  }

  async getBuildStatus(name: string): Promise<TurGitBuildStatus> {
    const response = await axios.get<TurGitBuildStatus>(`/git/repository/${name}/build/status`)
    return response.data
  }

  async listBranches(name: string): Promise<TurGitBranch[]> {
    const response = await axios.get<TurGitBranch[]>(`/git/repository/${name}/branches`)
    return response.data
  }

  async listTree(name: string, ref?: string, path?: string): Promise<TurGitTreeEntry[]> {
    const response = await axios.get<TurGitTreeEntry[]>(`/git/repository/${name}/tree`, {
      params: { ref, path },
    })
    return response.data
  }

  async readFile(name: string, path: string, ref?: string): Promise<string> {
    const response = await axios.get(`/git/repository/${name}/file`, {
      params: { path, ref },
      responseType: "text",
    })
    return response.data
  }
}
