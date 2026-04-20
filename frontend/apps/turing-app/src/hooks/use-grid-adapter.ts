import type { TurGridItem, TurGridItemAction } from "@/models/ui/grid-item";
import { useMemo } from "react";
type FieldExtractor<T> = keyof T | ((item: T) => string);

interface GridAdapterConfig<T> {
  id?: keyof T | ((item: T) => string | number);
  name: FieldExtractor<T>;
  description: FieldExtractor<T>;
  url: (item: T) => string;
  external?: boolean | ((item: T) => boolean);
  icon?: FieldExtractor<T>;
  actions?: (item: T) => TurGridItemAction[];
}

export function useGridAdapter<T>(
  data: T[] | undefined | null,
  config: GridAdapterConfig<T>,
): TurGridItem[] {
  return useMemo(() => {
    if (!Array.isArray(data) || data.length === 0) return [];

    const resolveField = (item: T, extractor: FieldExtractor<T>) => {
      if (typeof extractor === "function") {
        return extractor(item);
      }
      const value = item[extractor];
      return value === null || value === undefined ? "" : String(value);
    };

    const resolveId = (item: T) => {
      if (!config.id) {
        return (item as any).id;
      }
      if (typeof config.id === "function") {
        return config.id(item);
      }
      return item[config.id] as any;
    };

    return data.map((item) => ({
      id: resolveId(item),
      name: resolveField(item, config.name),
      description: resolveField(item, config.description),
      url: config.url(item),
      external: typeof config.external === "function" ? config.external(item) : config.external,
      icon: config.icon ? resolveField(item, config.icon) || null : null,
      actions: config.actions ? config.actions(item) : undefined,
    }));
  }, [data]);
}
