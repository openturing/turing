import * as XLSX from "xlsx";

export function exportToXlsx<T extends Record<string, unknown>>(
  data: T[],
  headers: { key: string; label: string }[],
  filename: string
) {
  const rows = data.map((item) => {
    const row: Record<string, unknown> = {};
    for (const h of headers) {
      const v = item[h.key];
      if (Array.isArray(v)) {
        row[h.label] = v.join(", ");
      } else if (v instanceof Date) {
        row[h.label] = v.toISOString();
      } else {
        row[h.label] = v;
      }
    }
    return row;
  });

  const ws = XLSX.utils.json_to_sheet(rows);

  // Auto-fit column widths
  const colWidths = headers.map((h) => {
    let max = h.label.length;
    for (const row of rows) {
      const len = String(row[h.label] ?? "").length;
      if (len > max) max = len;
    }
    return { wch: Math.min(Math.max(max + 2, 12), 60) };
  });
  ws["!cols"] = colWidths;

  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, "Logging");
  XLSX.writeFile(wb, `${filename}.xlsx`);
}
