import { useCallback, useEffect, useState } from "react"
import type { TurLLMInstance } from "@/models/llm/llm-instance.model"
import { TurChatService } from "@/services/chat/chat.service"
import { DEFAULT_CONTEXT_WINDOW, LLM_STORAGE_KEY } from "../chat.types"

const turChatService = new TurChatService()
const contextWindowCache = new Map<string, number>()

export function useLlmInstances() {
  const [llmInstances, setLlmInstances] = useState<TurLLMInstance[]>([])
  const [selectedLlmId, setSelectedLlmId] = useState<string>(() => localStorage.getItem(LLM_STORAGE_KEY) ?? "")
  const [fetchedContextWindow, setFetchedContextWindow] = useState<number | null>(null)
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    turChatService.queryLLMInstances().then((instances) => {
      const enabled = instances.filter((i) => i.enabled === 1)
      setLlmInstances(enabled)
      if (enabled.length > 0) {
        const stored = localStorage.getItem(LLM_STORAGE_KEY)
        const valid = stored && enabled.some((i) => i.id === stored)
        if (!valid) {
          setSelectedLlmId(enabled[0].id)
          localStorage.setItem(LLM_STORAGE_KEY, enabled[0].id)
        }
      }
    }).finally(() => setLoaded(true))
  }, [])

  useEffect(() => {
    if (!selectedLlmId) {
      setFetchedContextWindow(null)
      return
    }
    const cached = contextWindowCache.get(selectedLlmId)
    if (cached) {
      setFetchedContextWindow(cached)
      return
    }
    turChatService.fetchContextInfo(selectedLlmId).then((info) => {
      if (info.contextWindow > 0) {
        contextWindowCache.set(selectedLlmId, info.contextWindow)
        setFetchedContextWindow(info.contextWindow)
      }
    }).catch((err) => {
      console.warn("Failed to fetch context info:", err)
      setFetchedContextWindow(null)
    })
  }, [selectedLlmId])

  const selectLlm = useCallback((id: string) => {
    setSelectedLlmId(id)
    localStorage.setItem(LLM_STORAGE_KEY, id)
  }, [])

  const selectedInstance = llmInstances.find((i) => i.id === selectedLlmId)
  const contextWindow = fetchedContextWindow || selectedInstance?.contextWindow || DEFAULT_CONTEXT_WINDOW

  return {
    llmInstances,
    loaded,
    selectedLlmId,
    selectedInstance,
    contextWindow,
    selectLlm,
    turChatService,
  }
}
