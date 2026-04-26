/**
 * Formats a millisecond duration as a human-readable countdown string used
 * by progress UIs (export, import, RAG reindex).
 *
 * - {@code <=0} → {@code "--:--"} (unknown / not yet computable)
 * - {@code <1h}  → {@code "M:SS"}
 * - {@code >=1h} → {@code "H:MM:00"} (rounded to the nearest minute)
 *
 * @since 2026.2.4
 */
export function formatTime(ms: number): string {
  if (ms <= 0) return "--:--";
  const totalSec = Math.ceil(ms / 1000);
  if (totalSec >= 3600) {
    const hrs = Math.floor(totalSec / 3600);
    const min = Math.floor((totalSec % 3600) / 60);
    return `${hrs}:${min.toString().padStart(2, "0")}:00`;
  }
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min}:${sec.toString().padStart(2, "0")}`;
}
