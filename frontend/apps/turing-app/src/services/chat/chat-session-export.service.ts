import axios from "axios"

/**
 * Client for {@code GET /api/chat/sessions/{conversationId}/export} (T65).
 *
 * <p>The backend serves the full conversation snapshot — raw
 * {@code chat_flow_state} rows, finished submissions, the merged slot map, the
 * chat-memory transcript, and the T60 slot-write audit trail — as a downloadable
 * JSON attachment. This helper fetches it as a Blob and triggers a browser
 * download so the admin "Export conversation" button hands the operator a file
 * they can diff / attach to a bug report without dev-tools spelunking.
 *
 * @since 2026.3.1
 */
export class TurChatSessionExportService {
  async download(conversationId: string): Promise<void> {
    const response = await axios.get<Blob>(
      `/chat/sessions/${conversationId}/export`,
      { responseType: "blob" },
    )

    const safeId = conversationId.replace(/[^A-Za-z0-9._-]/g, "_").slice(0, 80)
    const url = URL.createObjectURL(response.data)
    try {
      const anchor = document.createElement("a")
      anchor.href = url
      anchor.download = `conversation-${safeId || "session"}.json`
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
    } finally {
      // Defer revocation so the click has a chance to start the download.
      setTimeout(() => URL.revokeObjectURL(url), 0)
    }
  }
}
