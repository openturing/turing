import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { GradientButton } from "@/components/ui/gradient-button";
import { IconColumns3, IconRotate } from "@tabler/icons-react";
import type { Table } from "@tanstack/react-table";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

/**
 * Dropdown that toggles which columns of a `@tanstack/react-table` table are
 * visible. Pair it with {@link usePersistedColumnVisibility} so the user's
 * choice survives a reload.
 *
 * @since 2026.2.8
 */
interface GridColumnVisibilityMenuProps<TData> {
  readonly table: Table<TData>;
  /** Map of column id → human-readable label shown in the menu. */
  readonly columnLabels?: Record<string, string>;
  /** Called when the user clicks the reset entry (typically clears persisted prefs). */
  readonly onReset?: () => void;
}

export function GridColumnVisibilityMenu<TData>({
  table,
  columnLabels,
  onReset,
}: GridColumnVisibilityMenuProps<TData>) {
  const { t } = useTranslation();
  const columns = table.getAllColumns().filter((c) => c.getCanHide());
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <GradientButton variant="outline" size="sm" className="h-8">
          <IconColumns3 className="h-4 w-4 mr-1" />
          {t("forms.common.columns", { defaultValue: "Columns" })}
        </GradientButton>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel>
          {t("forms.common.columnVisibility", { defaultValue: "Visible columns" })}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {columns.map((column) => (
          <DropdownMenuCheckboxItem
            key={column.id}
            checked={column.getIsVisible()}
            onCheckedChange={(value) => column.toggleVisibility(!!value)}
            onSelect={(e) => e.preventDefault()}
          >
            {columnLabels?.[column.id] ?? column.id}
          </DropdownMenuCheckboxItem>
        ))}
        {onReset && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={onReset}>
              <IconRotate className="h-4 w-4 mr-2" />
              {t("forms.common.resetColumns", { defaultValue: "Reset" })}
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/**
 * Persists column visibility to localStorage under a stable key. Returns the
 * state suitable for `useReactTable({ state: { columnVisibility }, onColumnVisibilityChange })`,
 * plus a `reset()` callback for the reset menu entry.
 *
 * @since 2026.2.8
 */
export function usePersistedColumnVisibility(storageKey: string) {
  const [columnVisibility, setColumnVisibility] = useState<Record<string, boolean>>(() => {
    try {
      const raw = localStorage.getItem(storageKey);
      return raw ? (JSON.parse(raw) as Record<string, boolean>) : {};
    } catch {
      return {};
    }
  });

  useEffect(() => {
    try {
      if (Object.keys(columnVisibility).length === 0) {
        localStorage.removeItem(storageKey);
      } else {
        localStorage.setItem(storageKey, JSON.stringify(columnVisibility));
      }
    } catch {
      // localStorage unavailable (private mode, quota) — silently ignore
    }
  }, [storageKey, columnVisibility]);

  const reset = useCallback(() => setColumnVisibility({}), []);

  return { columnVisibility, setColumnVisibility, reset };
}
