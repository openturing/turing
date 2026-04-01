import { createContext } from "react";
import type { TuringConfig } from "./types";

export interface TuringContextValue {
  config: TuringConfig;
}

export const TuringContext = createContext<TuringContextValue | null>(null);
