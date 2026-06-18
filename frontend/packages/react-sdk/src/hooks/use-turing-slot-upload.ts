import { useCallback, useState } from "react";
import {
  postSiteSlotUpload,
  type PostSlotUploadOptions,
  type TurChatSlotUploadResponse,
} from "../core/api";
import { readTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../core/session";
import { useTuringContext } from "../core/use-turing-context";

export type SlotUploadStatus = "idle" | "uploading" | "success" | "error";

export interface UseTuringSlotUploadOptions {
  readonly conversationId?: string;
  readonly sessionCookieName?: string;
  /** Default vision flag — overridable per {@link UseTuringSlotUploadReturn.upload}. */
  readonly vision?: boolean;
  /** Default scalar slot names to fill via vision when {@code vision} is on. */
  readonly visionSlotNames?: ReadonlyArray<string>;
}

export interface UseTuringSlotUploadReturn {
  /**
   * Uploads {@code file} to {@code slotName} on the current conversation. The
   * upload options override the hook-level defaults.
   */
  readonly upload: (
    slotName: string,
    file: File,
    overrides?: PostSlotUploadOptions,
  ) => Promise<TurChatSlotUploadResponse>;
  readonly status: SlotUploadStatus;
  readonly error: string | null;
  /** Most recent successful response, preserved for UI preview. */
  readonly lastResult: TurChatSlotUploadResponse | null;
}

/**
 * T401 — uploads an IMAGE / AUDIO / FILE to a multimodal slot (T64). With
 * {@code vision} the server runs a vision LLM to extract scalar slots from the
 * image.
 *
 * @example
 * ```tsx
 * const { upload, status } = useTuringSlotUpload({ vision: true });
 * <input type="file" onChange={(e) => upload("id_document", e.target.files![0])} />
 * ```
 *
 * @since 2026.3.4
 */
export function useTuringSlotUpload(
  options: UseTuringSlotUploadOptions = {},
): UseTuringSlotUploadReturn {
  const { config } = useTuringContext();
  const {
    conversationId: explicitConversationId,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
    vision: defaultVision,
    visionSlotNames: defaultVisionSlots,
  } = options;

  const [status, setStatus] = useState<SlotUploadStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<TurChatSlotUploadResponse | null>(null);

  const upload = useCallback(
    async (
      slotName: string,
      file: File,
      overrides?: PostSlotUploadOptions,
    ): Promise<TurChatSlotUploadResponse> => {
      const conversationId =
        explicitConversationId ?? readTurSession(sessionCookieName);
      if (!conversationId) {
        const msg = "No active conversation — send a chat message first.";
        setError(msg);
        setStatus("error");
        throw new Error(msg);
      }
      setStatus("uploading");
      setError(null);
      try {
        const result = await postSiteSlotUpload(
          config.site,
          conversationId,
          slotName,
          file,
          {
            vision: overrides?.vision ?? defaultVision,
            visionSlotNames: overrides?.visionSlotNames ?? defaultVisionSlots,
          },
        );
        if (result.error) {
          setError(result.error);
          setStatus("error");
          return result;
        }
        setLastResult(result);
        setStatus("success");
        return result;
      } catch (err) {
        const msg = err instanceof Error ? err.message : "Slot upload failed";
        setError(msg);
        setStatus("error");
        throw err;
      }
    },
    [
      config.site,
      explicitConversationId,
      sessionCookieName,
      defaultVision,
      defaultVisionSlots,
    ],
  );

  return { upload, status, error, lastResult };
}
