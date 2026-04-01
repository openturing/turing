import { useContext } from "react";
import { TuringContext, type TuringContextValue } from "./context";

export function useTuringContext(): TuringContextValue {
  const ctx = useContext(TuringContext);
  if (!ctx) {
    throw new Error(
      "useTuringContext must be used within a <TuringProvider>."
    );
  }
  return ctx;
}
