import axios from "axios"

/**
 * One row of the T60 slot audit log. Mirrors {@code TurChatSlotAuditDto.Entry}
 * on the backend. {@code source} is the enum name ({@code NODE} | {@code TOOL}
 * | {@code ENDPOINT} | {@code EXTRACT}); {@code originDetail} is a free-text
 * tag captured at the write site (node id, custom tool title, site name,
 * agent id) so the timeline can render a contextual subtitle.
 *
 * @since 2026.3.1
 */
export interface TurChatSlotAuditEntry {
  id: string
  slotName: string
  oldValue: string | null
  newValue: string | null
  source: "NODE" | "TOOL" | "ENDPOINT" | "EXTRACT" | string
  originDetail: string | null
  ts: string
}

export interface TurChatSlotAuditTrail {
  conversationId: string
  entries: TurChatSlotAuditEntry[]
}

/**
 * Client for {@code GET /api/chat/sessions/{conversationId}/slot-audit}. Used
 * by the SlotInspector timeline inside {@code ChatSessionInfoSheet} so
 * operators can replay slot writes with source attribution.
 *
 * @since 2026.3.1
 */
export class TurChatSlotAuditService {
  async query(conversationId: string): Promise<TurChatSlotAuditTrail> {
    const response = await axios.get<TurChatSlotAuditTrail>(
      `/chat/sessions/${conversationId}/slot-audit`,
    )
    return response.data
  }
}
