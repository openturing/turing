import * as XLSX from "xlsx";

export function exportToXlsx<T extends Record<string, unknown>>(
  data: T[],
  headers: { key: string; label: string }[],
  filename: string
) {
  const rows = data.map((item) =>
    headers.reduce(
      (acc, h) => {
        const value = item[h.key];
        acc[h.label] = Array.isArray(value) ? value.join(", ") : value instanceof Date ? value.toISOString() : value;
        return acc;
      },
      {} as Record<string, unknown>
    )
  );

  const ws = XLSX.utils.json_to_sheet(rows);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, "Logging");
  XLSX.writeFile(wb, `${filename}.xlsx`);
}
