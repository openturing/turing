import { SectionCard } from "@/components/ui/section-card";
import type {
  TurCapabilityDescriptor,
  TurInstanceCapabilityRow,
} from "@/models/genai/capability.model.ts";
import { TurCapabilityRegistryService } from "@/services/genai/capability.service";
import { IconCheck, IconGridDots, IconLoader2, IconMinus } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

const service = new TurCapabilityRegistryService();

/** OPENAI → "openai" etc.; TURING/ANY are not vendor-native so never columns. */
function providerPlugin(provider: string): string {
  return provider.toLowerCase();
}

type CellState = "wired" | "capable" | "na";

function cellState(
  row: TurInstanceCapabilityRow,
  cap: TurCapabilityDescriptor,
): CellState {
  if (providerPlugin(cap.provider) !== row.pluginType) return "na";
  return row.enabledCapabilityKeys.includes(cap.key.toLowerCase()) ? "wired" : "capable";
}

/**
 * T186 / §X.14.f — admin (LLM instance × capability) heatmap. Renders from the
 * T432 registry (provider-native TOOL rows, grouped by provider × category) and
 * the T186 `/api/capability/matrix` per-instance enabled state, colouring each
 * cell wired / capable-but-not-wired / not-applicable.
 */
export default function CapabilityMatrixPage() {
  const { t } = useTranslation();
  const [registry, setRegistry] = useState<TurCapabilityDescriptor[]>([]);
  const [rows, setRows] = useState<TurInstanceCapabilityRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    Promise.all([service.query(), service.matrix()])
      .then(([reg, matrix]) => {
        setRegistry(reg);
        setRows(matrix);
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  // Columns = provider-native TOOL capabilities, grouped by provider × category.
  const columns = useMemo(() => {
    return registry
      .filter((c) => c.kind === "TOOL" && c.provider !== "TURING" && c.provider !== "ANY")
      .sort(
        (a, b) =>
          a.provider.localeCompare(b.provider) ||
          a.category.localeCompare(b.category) ||
          a.label.localeCompare(b.label),
      );
  }, [registry]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <IconLoader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error) {
    return <p className="px-6 py-6 text-sm text-destructive">{t("capabilityMatrix.loadFailed")}</p>;
  }

  return (
    <div className="py-6 px-6">
      <SectionCard variant="violet">
        <SectionCard.StaticHeader
          icon={IconGridDots}
          title={t("capabilityMatrix.title")}
          description={t("capabilityMatrix.description")}
        />
        <SectionCard.Content>
          <div className="flex flex-wrap gap-4 text-xs text-muted-foreground pb-2">
            <span className="inline-flex items-center gap-1.5">
              <IconCheck className="size-4 text-emerald-500" /> {t("capabilityMatrix.legendWired")}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="size-3 rounded-full border border-muted-foreground/50" />
              {t("capabilityMatrix.legendCapable")}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <IconMinus className="size-4 text-muted-foreground/40" /> {t("capabilityMatrix.legendNa")}
            </span>
          </div>

          {rows.length === 0 || columns.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">
              {t("capabilityMatrix.empty")}
            </p>
          ) : (
            <div className="rounded-lg border overflow-x-auto">
              <table className="w-full text-sm border-collapse">
                <thead className="bg-muted/60">
                  <tr>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground sticky left-0 bg-muted/60 z-10">
                      {t("capabilityMatrix.instance")}
                    </th>
                    {columns.map((c) => (
                      <th
                        key={c.key}
                        className="px-2 py-2 font-medium text-muted-foreground text-center align-bottom whitespace-nowrap"
                        title={`${c.provider} · ${c.category} · ${c.description}`}
                      >
                        <div className="text-[10px] uppercase tracking-wide text-muted-foreground/60">
                          {c.provider.toLowerCase()}
                        </div>
                        <div className="text-xs">{c.label}</div>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.instanceId} className="border-t hover:bg-muted/30 transition-colors">
                      <td className="px-3 py-2 sticky left-0 bg-background z-10">
                        <div className="font-medium">{row.title}</div>
                        <div className="text-[11px] font-mono text-muted-foreground/70">
                          {row.pluginType}
                          {!row.enabled && ` · ${t("capabilityMatrix.disabled")}`}
                        </div>
                      </td>
                      {columns.map((c) => {
                        const state = cellState(row, c);
                        return (
                          <td key={c.key} className="px-2 py-2 text-center">
                            {state === "wired" && (
                              <IconCheck className="size-4 text-emerald-500 inline" />
                            )}
                            {state === "capable" && (
                              <span className="inline-block size-3 rounded-full border border-muted-foreground/50" />
                            )}
                            {state === "na" && (
                              <IconMinus className="size-4 text-muted-foreground/30 inline" />
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </SectionCard.Content>
      </SectionCard>
    </div>
  );
}
