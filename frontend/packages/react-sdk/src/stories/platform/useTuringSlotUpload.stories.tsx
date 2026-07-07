import type { Meta, StoryObj } from "@storybook/react-vite";
import { useEffect, useRef, useState } from "react";

/**
 * # useTuringSlotUpload
 *
 * Uploads a file into a **multimodal chat slot** — an `IMAGE`, `AUDIO`, or
 * `FILE` slot whose value is a storage-backed object rather than a scalar
 * string. The hook drives the `POST /sn/{site}/chat/slot-upload` round-trip on
 * the current conversation, exposes a four-state `status`
 * (`idle → uploading → success | error`), and preserves the last successful
 * `TurChatSlotUploadResponse` for rendering a filled-slot preview chip.
 *
 * The conversation is resolved automatically from the `TUR_SESSION` cookie (or
 * an explicit `conversationId` option) — the visitor must have sent at least
 * one chat message first so a conversation exists to attach the object to.
 *
 * With the optional `vision` flag, the server runs a vision LLM over an
 * uploaded image and writes the extracted scalar values into the named
 * `visionSlotNames`, so a single photo can fill several text slots at once
 * (e.g. uploading an ID document fills `full_name`, `doc_number`, `birth_date`).
 *
 * ## Key Features
 * - `upload(slotName, file, overrides?)`: imperative upload, resolves with the
 *   full `TurChatSlotUploadResponse` (`objectName`, `url`, `contentType`,
 *   `size`, `visionExtracted`, `visionSlotsWritten`)
 * - `status`: `"idle" | "uploading" | "success" | "error"` — bind progress UI
 * - `error`: human-readable message from the last failed upload
 * - `lastResult`: most recent successful response, kept for a preview chip
 * - Resolves the conversation from the `TUR_SESSION` cookie automatically
 * - Optional `vision` + `visionSlotNames`: fill scalar slots from an image
 *
 * ## Usage
 * ```tsx
 * const { upload, status, error, lastResult } = useTuringSlotUpload({
 *   vision: true,
 *   visionSlotNames: ["full_name", "doc_number"],
 * });
 *
 * <input
 *   type="file"
 *   accept="image/*,application/pdf"
 *   disabled={status === "uploading"}
 *   onChange={(e) => {
 *     const file = e.target.files?.[0];
 *     if (file) void upload("id_document", file);
 *   }}
 * />
 *
 * {status === "uploading" && <ProgressBar />}
 * {status === "error" && <p role="alert">{error}</p>}
 * {lastResult && (
 *   <a href={lastResult.url}>
 *     ✓ {lastResult.objectName} ({lastResult.contentType})
 *   </a>
 * )}
 * ```
 *
 * ## When to use
 * Pair with a chat flow that has a `slot` node typed `IMAGE` / `AUDIO` /
 * `FILE`. The flow advances past that node once the slot is filled — exactly as
 * `useTuringSlotWriter` does for scalar slots, but for binary uploads. Most
 * useful when:
 *
 * - **Document intake**: the assistant asks for a photo of an ID, receipt, or
 *   contract; the visitor drops the file and the flow continues
 * - **Vision pre-fill**: turn `vision` on so one photo populates several text
 *   slots — no manual typing, fewer chat turns
 * - **Rich attachments**: audio notes or PDFs the agent later reasons over,
 *   referenced by the signed `url` in subsequent turns
 *
 * ## About this story
 * Fully self-contained — there is **no real hook, Provider, network, or file
 * I/O**. "Selecting" a file (via the picker or the drag-drop zone) fabricates
 * plausible metadata, then a `setInterval` animates a progress bar through
 * `idle → uploading → success`, ending in a filled-slot chip. The drop zone
 * reacts visually to drag events but never reads file bytes.
 */

type Status = "idle" | "uploading" | "success" | "error";

/** Mirror of the slot kinds backed by storage (IMAGE / AUDIO / FILE). */
type SlotKind = "image" | "audio" | "file";

interface FauxFile {
  readonly name: string;
  readonly size: number;
  readonly contentType: string;
  readonly kind: SlotKind;
  readonly icon: string;
}

/** Mirror of the SDK's `TurChatSlotUploadResponse` for the preview chip. */
interface FauxUploadResponse {
  readonly slotName: string;
  readonly slotType: string;
  readonly objectName: string;
  readonly url: string;
  readonly contentType: string;
  readonly size: number;
  readonly visionExtracted: Readonly<Record<string, string>>;
  readonly visionSlotsWritten: number;
}

const GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

const SAMPLE_FILES: ReadonlyArray<FauxFile> = [
  { name: "rg-frente.jpg", size: 1_842_311, contentType: "image/jpeg", kind: "image", icon: "🖼️" },
  { name: "comprovante.pdf", size: 512_004, contentType: "application/pdf", kind: "file", icon: "📄" },
  { name: "nota-de-voz.m4a", size: 3_204_880, contentType: "audio/mp4", kind: "audio", icon: "🎧" },
  { name: "selfie.png", size: 2_115_900, contentType: "image/png", kind: "image", icon: "🖼️" },
];

const SLOT_NAME = "id_document";
const VISION_EXTRACTED: Readonly<Record<string, string>> = {
  full_name: "Marina Albuquerque",
  doc_number: "12.345.678-9",
  birth_date: "1990-04-17",
};

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function slotTypeFor(kind: SlotKind): string {
  return kind.toUpperCase();
}

function SlotUploadDemo({
  vision,
  failProbability,
  dark,
}: {
  vision: boolean;
  failProbability: number;
  dark: boolean;
}) {
  const [status, setStatus] = useState<Status>("idle");
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<FauxFile | null>(null);
  const [lastResult, setLastResult] = useState<FauxUploadResponse | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [pickIdx, setPickIdx] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Theme tokens — light vs dark surfaces.
  const bg = dark ? "#16161f" : "#ffffff";
  const panel = dark ? "#1e1e2e" : "#f8fafc";
  const border = dark ? "#2a2a3c" : "#e2e8f0";
  const text = dark ? "#e2e8f0" : "#0f172a";
  const muted = dark ? "#94a3b8" : "#64748b";

  function clearTimer() {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }

  useEffect(() => clearTimer, []);

  // Simulates `upload(slotName, file)` — animates the progress bar through
  // uploading → success/error. No bytes are ever read.
  function simulateUpload(file: FauxFile) {
    if (status === "uploading") return;
    clearTimer();
    setPending(file);
    setError(null);
    setProgress(0);
    setStatus("uploading");

    timerRef.current = setInterval(() => {
      setProgress((prev) => {
        const next = prev + 7 + Math.random() * 9;
        if (next >= 100) {
          clearTimer();
          // Decide success/error once the transfer "completes".
          if (Math.random() < failProbability) {
            setError("Storage backend rejected the upload (simulated 502).");
            setStatus("error");
            setPending(null);
          } else {
            setLastResult({
              slotName: SLOT_NAME,
              slotType: slotTypeFor(file.kind),
              objectName: `tenants/demo/conv-7f3a/${file.name}`,
              url: `https://storage.viglet.dev/signed/${encodeURIComponent(file.name)}?sig=demo`,
              contentType: file.contentType,
              size: file.size,
              visionExtracted: vision && file.kind === "image" ? VISION_EXTRACTED : {},
              visionSlotsWritten: vision && file.kind === "image" ? Object.keys(VISION_EXTRACTED).length : 0,
            });
            setStatus("success");
            setPending(null);
          }
          return 100;
        }
        return next;
      });
    }, 110);
  }

  function pickNext() {
    const file = SAMPLE_FILES[pickIdx % SAMPLE_FILES.length];
    setPickIdx((i) => i + 1);
    simulateUpload(file);
  }

  function reset() {
    clearTimer();
    setStatus("idle");
    setProgress(0);
    setError(null);
    setPending(null);
    setLastResult(null);
  }

  const statusColors: Record<Status, { bg: string; fg: string }> = {
    idle: { bg: dark ? "#2a2a3c" : "#e2e8f0", fg: dark ? "#cbd5e1" : "#475569" },
    uploading: { bg: dark ? "#3a2e0a" : "#fef3c7", fg: dark ? "#fcd34d" : "#92400e" },
    success: { bg: dark ? "#0f2e1a" : "#dcfce7", fg: dark ? "#4ade80" : "#166534" },
    error: { bg: dark ? "#3a0f12" : "#fee2e2", fg: dark ? "#f87171" : "#991b1b" },
  };

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", maxWidth: "560px", color: text }}>
      {/* Assistant chat bubble asking for the upload */}
      <div
        style={{
          display: "flex",
          gap: "10px",
          alignItems: "flex-start",
          marginBottom: "14px",
        }}
      >
        <div
          style={{
            width: 32,
            height: 32,
            flexShrink: 0,
            borderRadius: "50%",
            background: GRADIENT,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "white",
            fontSize: "14px",
            fontWeight: 700,
          }}
          aria-hidden="true"
        >
          M
        </div>
        <div
          style={{
            padding: "12px 14px",
            background: panel,
            border: `1px solid ${border}`,
            borderRadius: "12px",
            borderTopLeftRadius: "2px",
            fontSize: "14px",
            lineHeight: 1.5,
          }}
        >
          Para confirmar sua matrícula, você pode enviar uma <strong>foto do seu
          documento</strong> {vision ? "— eu leio os dados automaticamente." : "?"}
        </div>
      </div>

      {/* Drop zone */}
      <div
        onDragOver={(e) => {
          e.preventDefault();
          if (status !== "uploading") setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          if (status !== "uploading") pickNext();
        }}
        onClick={() => {
          if (status !== "uploading") pickNext();
        }}
        role="button"
        tabIndex={0}
        style={{
          border: `2px dashed ${dragOver ? "#4f46e5" : border}`,
          borderRadius: "14px",
          padding: "26px 18px",
          textAlign: "center",
          cursor: status === "uploading" ? "wait" : "pointer",
          background: dragOver ? (dark ? "#1c1c33" : "#eef2ff") : bg,
          transition: "background 120ms, border-color 120ms",
          marginBottom: "14px",
        }}
      >
        <div style={{ fontSize: "32px", marginBottom: "6px" }}>📎</div>
        <div style={{ fontSize: "14px", fontWeight: 600 }}>
          {dragOver ? "Solte o arquivo aqui" : "Arraste um arquivo ou clique para selecionar"}
        </div>
        <div style={{ fontSize: "12px", color: muted, marginTop: "4px" }}>
          (demo: gera um arquivo fictício — imagem, áudio ou PDF)
        </div>
      </div>

      {/* Progress bar — visible while uploading or after a transfer */}
      {(status === "uploading" || status === "success" || status === "error") && pending !== null && (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "10px",
            padding: "10px 14px",
            background: panel,
            border: `1px solid ${border}`,
            borderRadius: "10px",
            marginBottom: "12px",
          }}
        >
          <span style={{ fontSize: "18px" }} aria-hidden="true">{pending.icon}</span>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                fontSize: "12px",
                marginBottom: "5px",
              }}
            >
              <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                {pending.name}
              </span>
              <span style={{ color: muted }}>{Math.min(100, Math.round(progress))}%</span>
            </div>
            <div
              style={{
                height: 8,
                borderRadius: "999px",
                background: dark ? "#2a2a3c" : "#e2e8f0",
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  height: "100%",
                  width: `${Math.min(100, progress)}%`,
                  background: GRADIENT,
                  borderRadius: "999px",
                  transition: "width 110ms linear",
                }}
              />
            </div>
          </div>
        </div>
      )}

      {/* Status + reset row */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          padding: "10px 14px",
          background: panel,
          border: `1px solid ${border}`,
          borderRadius: "10px",
          fontSize: "12px",
          marginBottom: "12px",
        }}
      >
        <span>
          <strong>status:</strong>{" "}
          <code
            style={{
              padding: "2px 8px",
              borderRadius: "4px",
              background: statusColors[status].bg,
              color: statusColors[status].fg,
            }}
          >
            {status}
          </code>
        </span>
        <button
          type="button"
          onClick={reset}
          disabled={status === "uploading"}
          style={{
            padding: "5px 12px",
            borderRadius: "8px",
            border: `1px solid ${border}`,
            background: bg,
            color: text,
            fontSize: "12px",
            cursor: status === "uploading" ? "not-allowed" : "pointer",
            opacity: status === "uploading" ? 0.5 : 1,
          }}
        >
          Reset
        </button>
      </div>

      {/* Error banner */}
      {status === "error" && error && (
        <div
          role="alert"
          style={{
            padding: "10px 14px",
            background: dark ? "#3a0f12" : "#fef2f2",
            border: `1px solid ${dark ? "#7f1d1d" : "#fecaca"}`,
            borderRadius: "8px",
            fontSize: "13px",
            color: dark ? "#f87171" : "#991b1b",
            marginBottom: "12px",
          }}
        >
          ⚠ {error}
        </div>
      )}

      {/* Filled-slot preview chip */}
      {status === "success" && lastResult && (
        <div
          style={{
            border: `1px solid ${dark ? "#1d4ed8" : "#bfdbfe"}`,
            borderRadius: "12px",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              padding: "8px 14px",
              background: dark ? "#0f172a" : "#eff6ff",
              borderBottom: `1px solid ${dark ? "#1d4ed8" : "#bfdbfe"}`,
              fontSize: "11px",
              fontWeight: 700,
              textTransform: "uppercase",
              letterSpacing: "0.05em",
              color: dark ? "#60a5fa" : "#1d4ed8",
            }}
          >
            ✓ Slot filled · {lastResult.slotName} ({lastResult.slotType})
          </div>

          {/* Chip with file metadata */}
          <div style={{ padding: "12px 14px" }}>
            <div
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: "8px",
                padding: "8px 12px",
                borderRadius: "999px",
                background: dark ? "#1e1e2e" : "#f1f5f9",
                fontSize: "13px",
              }}
            >
              <span aria-hidden="true">{pending?.icon ?? "📎"}</span>
              <code style={{ color: text }}>{lastResult.objectName.split("/").pop()}</code>
              <span style={{ color: muted }}>·</span>
              <span style={{ color: muted }}>{lastResult.contentType}</span>
              <span style={{ color: muted }}>·</span>
              <span style={{ color: muted }}>{formatBytes(lastResult.size)}</span>
            </div>

            {/* Vision-extracted scalar slots */}
            {lastResult.visionSlotsWritten > 0 && (
              <div style={{ marginTop: "12px" }}>
                <div style={{ fontSize: "11px", color: muted, marginBottom: "6px" }}>
                  🖼️ Vision filled {lastResult.visionSlotsWritten} scalar slot(s):
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                  {Object.entries(lastResult.visionExtracted).map(([k, v]) => (
                    <div
                      key={k}
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        fontSize: "12px",
                        padding: "4px 8px",
                        borderRadius: "6px",
                        background: dark ? "#16161f" : "#f8fafc",
                      }}
                    >
                      <code style={{ color: dark ? "#93c5fd" : "#2563eb" }}>{k}</code>
                      <span style={{ color: text }}>{v}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

const meta: Meta<typeof SlotUploadDemo> = {
  title: "Platform/useTuringSlotUpload",
  component: SlotUploadDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Uploads a file into a multimodal chat slot (IMAGE / AUDIO / FILE) on the current conversation, exposing an idle→uploading→success/error status and the last response for a preview chip. With `vision` on, the server fills scalar slots from an image. This story is a fully self-contained simulation — no real network, file I/O, or Provider; selecting a file fabricates metadata and animates the progress bar.",
      },
    },
  },
  argTypes: {
    vision: {
      description:
        "Run a vision LLM over an uploaded image to fill named scalar slots (only applies to image files in this demo).",
      control: { type: "boolean" },
    },
    failProbability: {
      description: "Probability the simulated upload fails once the transfer completes (0 = never, 1 = always).",
      control: { type: "range", min: 0, max: 1, step: 0.05 },
    },
    dark: {
      description: "Render the dark-mode surface (matches the Turing email/dark palette).",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof SlotUploadDemo>;

export const Default: Story = {
  name: "📎 Drop zone → upload → filled chip",
  args: { vision: false, failProbability: 0, dark: false },
};

export const VisionExtraction: Story = {
  name: "🖼️ Vision pre-fill (image → scalar slots)",
  args: { vision: true, failProbability: 0, dark: false },
};

export const ErrorPath: Story = {
  name: "⚠️ Error path (storage rejects upload)",
  args: { vision: false, failProbability: 1, dark: false },
};

export const Dark: Story = {
  name: "🌙 Dark mode with vision",
  args: { vision: true, failProbability: 0, dark: true },
};
