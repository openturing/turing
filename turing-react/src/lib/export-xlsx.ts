import ExcelJS from "exceljs";
import { saveAs } from "file-saver";

const HEADER_FILL: ExcelJS.Fill = {
  type: "pattern",
  pattern: "solid",
  fgColor: { argb: "FF2563EB" },
};

const HEADER_FONT: Partial<ExcelJS.Font> = {
  bold: true,
  color: { argb: "FFFFFFFF" },
  size: 11,
};

const BORDER: Partial<ExcelJS.Border> = {
  style: "thin",
  color: { argb: "FFD0D5DD" },
};

const CELL_BORDERS: Partial<ExcelJS.Borders> = {
  top: BORDER,
  left: BORDER,
  bottom: BORDER,
  right: BORDER,
};

const STRIPE_FILL: ExcelJS.Fill = {
  type: "pattern",
  pattern: "solid",
  fgColor: { argb: "FFF1F5F9" },
};

export async function exportToXlsx<T extends Record<string, unknown>>(
  data: T[],
  headers: { key: string; label: string }[],
  filename: string
) {
  const wb = new ExcelJS.Workbook();
  wb.creator = "Viglet Turing ES";
  wb.created = new Date();

  const ws = wb.addWorksheet("Logging", {
    views: [{ state: "frozen", ySplit: 1 }],
  });

  ws.columns = headers.map((h) => ({
    header: h.label,
    key: h.key,
    width: 18,
  }));

  // --- header row ---
  const headerRow = ws.getRow(1);
  headerRow.height = 28;
  headerRow.eachCell((cell) => {
    cell.fill = HEADER_FILL;
    cell.font = HEADER_FONT;
    cell.alignment = { vertical: "middle", horizontal: "center" };
    cell.border = CELL_BORDERS;
  });

  // --- data rows ---
  for (const item of data) {
    const rowValues: Record<string, unknown> = {};
    for (const h of headers) {
      const v = item[h.key];
      if (Array.isArray(v)) {
        rowValues[h.key] = v.join(", ");
      } else if (v instanceof Date) {
        rowValues[h.key] = v.toISOString();
      } else {
        rowValues[h.key] = v;
      }
    }
    ws.addRow(rowValues);
  }

  // --- style data rows ---
  for (let r = 2; r <= ws.rowCount; r++) {
    const row = ws.getRow(r);
    row.height = 22;
    row.eachCell({ includeEmpty: true }, (cell) => {
      cell.border = CELL_BORDERS;
      cell.alignment = { vertical: "middle", wrapText: true };
      cell.font = { size: 10 };
      if (r % 2 === 0) {
        cell.fill = STRIPE_FILL;
      }
    });
  }

  // --- auto-fit column widths ---
  for (const col of ws.columns) {
    let maxLen = String(col.header ?? "").length;
    col.eachCell?.({ includeEmpty: false }, (cell) => {
      const len = String(cell.value ?? "").length;
      if (len > maxLen) maxLen = len;
    });
    col.width = Math.min(Math.max(maxLen + 4, 12), 60);
  }

  // --- autoFilter on header ---
  ws.autoFilter = {
    from: { row: 1, column: 1 },
    to: { row: 1, column: headers.length },
  };

  const buffer = await wb.xlsx.writeBuffer();
  saveAs(new Blob([buffer], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }), `${filename}.xlsx`);
}
