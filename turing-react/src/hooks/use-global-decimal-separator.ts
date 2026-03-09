import type { TurGlobalDecimalSeparator } from "@/models/system/global-settings.model";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";
import { useEffect, useMemo, useState } from "react";

let cachedSeparator: TurGlobalDecimalSeparator | null = null;
let cachedPromise: Promise<TurGlobalDecimalSeparator> | null = null;

const globalSettingsService = new TurGlobalSettingsService();

const fetchDecimalSeparator = async (): Promise<TurGlobalDecimalSeparator> => {
  if (cachedSeparator) {
    return cachedSeparator;
  }
  if (cachedPromise) {
    return cachedPromise;
  }

  cachedPromise = globalSettingsService
    .query()
    .then((settings) => {
      cachedSeparator = settings.decimalSeparator ?? "DOT";
      cachedPromise = null;
      return cachedSeparator;
    })
    .catch(() => {
      cachedPromise = null;
      return "DOT";
    });

  return cachedPromise;
};

const normalizeNumericPart = (
  value: string,
  decimalSeparator: TurGlobalDecimalSeparator,
): string => {
  const raw = value.trim();
  if (!raw) {
    return "";
  }

  if (decimalSeparator === "COMMA") {
    return raw.replace(/\./g, ",");
  }

  return raw.replace(/,/g, ".");
};

const looksLikeDecimal = (value: string): boolean => {
  const trimmed = value.trim();
  if (!trimmed) {
    return false;
  }
  if (/\d{4}-\d{2}-\d{2}/.test(trimmed)) {
    return false;
  }
  return /^[+\-]?\d[\d\s.,]*$/.test(trimmed);
};

export function useGlobalDecimalSeparator() {
  const [decimalSeparator, setDecimalSeparator] =
    useState<TurGlobalDecimalSeparator>("DOT");

  useEffect(() => {
    fetchDecimalSeparator().then(setDecimalSeparator);
  }, []);

  const decimalSymbol = decimalSeparator === "COMMA" ? "," : ".";

  return useMemo(
    () => ({
      decimalSeparator,
      decimalSymbol,
      normalizeDecimalString: (value: string) =>
        normalizeNumericPart(value, decimalSeparator),
      normalizeCurrencyString: (value: string) => {
        const trimmed = (value ?? "").trim();
        if (!trimmed) {
          return "";
        }

        const lastCommaIndex = trimmed.lastIndexOf(",");
        if (lastCommaIndex <= 0) {
          return normalizeNumericPart(trimmed, decimalSeparator);
        }

        const amount = normalizeNumericPart(
          trimmed.substring(0, lastCommaIndex),
          decimalSeparator,
        );
        const code = trimmed
          .substring(lastCommaIndex + 1)
          .toUpperCase()
          .replace(/[^A-Z]/g, "")
          .slice(0, 3);

        return code ? `${amount},${code}` : amount;
      },
      normalizeMaybeDecimalString: (value: string) => {
        if (!looksLikeDecimal(value)) {
          return value;
        }
        return normalizeNumericPart(value, decimalSeparator);
      },
    }),
    [decimalSeparator, decimalSymbol],
  );
}
