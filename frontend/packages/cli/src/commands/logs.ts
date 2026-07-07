/**
 * {@code turing logs --conversation <id>} — tails live chat events for a
 * conversation over the spectator SSE stream
 * ({@code /api/chat/sessions/{id}/spectate/stream}). Prints a transcript
 * snapshot first, then each new turn as it lands. Ctrl-C ends the stream.
 *
 * <p>Turing's event surface is conversation-scoped (there is no global "tail
 * all chats" firehose), so a conversation id is required.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import type { TuringClient } from "../client.js";

export interface LogsArgs {
  conversationId: string;
  /** Also relay slot writes on a second stream. */
  slots?: boolean;
}

interface ChatMessageEvent {
  role?: string;
  content?: string;
  isSnapshot?: boolean;
}

interface SlotsEvent {
  slots?: Record<string, string>;
}

export async function runLogs(
  client: TuringClient,
  args: LogsArgs,
  log: (line: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  await client.authenticate();
  const id = encodeURIComponent(args.conversationId);
  log(`Tailing conversation ${args.conversationId} (Ctrl-C to stop)…`);

  const tasks: Promise<void>[] = [
    client.streamSse(`/api/chat/sessions/${id}/spectate/stream`, (data) => {
      try {
        const ev = JSON.parse(data) as ChatMessageEvent;
        const tag = ev.isSnapshot ? "·" : ">";
        log(`${tag} [${ev.role ?? "?"}] ${ev.content ?? ""}`);
      } catch {
        // ignore keep-alives
      }
    }, signal),
  ];

  if (args.slots) {
    tasks.push(
      client.streamSse(`/api/chat/sessions/${id}/spectate/slots/stream`, (data) => {
        try {
          const ev = JSON.parse(data) as SlotsEvent;
          if (ev.slots) log(`= slots ${JSON.stringify(ev.slots)}`);
        } catch {
          // ignore
        }
      }, signal),
    );
  }

  await Promise.all(tasks);
}
