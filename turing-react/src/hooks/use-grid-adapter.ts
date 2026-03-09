import type { TurGridItem } from "@/models/ui/grid-item";
import { useMemo } from "react";
type FieldExtractor<T> = keyof T | ((item: T) => string);

interface GridAdapterConfig<T> {
  id?: keyof T | ((item: T) => string | number);
  name: FieldExtractor<T>;
  description: FieldExtractor<T>;
  url: (item: T) => string;
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
      return String(item[extractor]);
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
    }));
  }, [data]);
}
