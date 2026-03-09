import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export const truncateMiddle = (
  text: string,
  maxLength: number = 10,
): string => {
  if (!text || text.length <= maxLength) return text ?? "";

  const dots = "...";
  const charsToShow = maxLength - dots.length;
  const frontChars = Math.ceil(charsToShow / 2);
  const backChars = Math.floor(charsToShow / 2);

  return (
    text.substring(0, frontChars) +
    dots +
    text.substring(text.length - backChars)
  );
};

export const getFlagEmoji = (locale: string) => {
  const countryCode = locale.split("_")[1]?.toUpperCase();

  if (!countryCode?.length || countryCode.length !== 2) return "🌐";

  return countryCode
    .split("")
    .map((char) => String.fromCodePoint((char.codePointAt(0) ?? 0) + 127397))
    .join("");
};

export const getHashedColor = (str: string) => {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (str.codePointAt(i) ?? 0) + ((hash << 5) - hash);
  }
  const h = Math.abs(hash) % 360;
  return {
    h,
    light: {
      bg: `hsl(${h}, 70%, 95%)`,
      text: `hsl(${h}, 70%, 20%)`,
      border: `hsl(${h}, 70%, 90%)`,
    },
    dark: {
      bg: `hsl(${h}, 50%, 15%)`,
      text: `hsl(${h}, 80%, 80%)`,
      border: `hsl(${h}, 50%, 25%)`,
    },
  };
};
