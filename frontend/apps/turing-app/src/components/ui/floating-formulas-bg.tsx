import axios from "axios";
import { useEffect, useMemo, useState } from "react";
import "./floating-formulas-bg.css";

const FORMULAS = [
  "H\u2082O", "CO\u2082", "NaCl", "O\u2082", "NH\u2083", "CH\u2084",
  "Fe\u2082O\u2083", "CaCO\u2083", "HCl", "NaOH", "N\u2082", "SiO\u2082",
  "Cu", "H\u2082O\u2082", "SO\u2083", "AgNO\u2083", "Cl\u2082", "MgO",
];

const DRIFT_CLASSES = ["ff-drift-a", "ff-drift-b", "ff-drift-c"];

interface Bond {
  top: string; left: string; size: number;
  duration: number; delay: number; drift: string;
  opacity: number; path: string;
}

const BONDS: Bond[] = [
  {
    top: "10%", left: "8%", size: 50, duration: 18, delay: -2,
    drift: "ff-drift-b", opacity: 0.2,
    path: "M8,25 L25,12 L42,25 M25,12 L25,3",
  },
  {
    top: "60%", left: "80%", size: 45, duration: 16, delay: -5,
    drift: "ff-drift-a", opacity: 0.18,
    path: "M8,22 L22,8 L36,22 L22,36 Z",
  },
  {
    top: "25%", left: "70%", size: 55, duration: 20, delay: -3,
    drift: "ff-drift-c", opacity: 0.15,
    path: "M10,28 L28,10 L46,28 M28,10 L28,2 M10,28 L2,36 M46,28 L54,36",
  },
  {
    top: "75%", left: "20%", size: 40, duration: 15, delay: -7,
    drift: "ff-drift-a", opacity: 0.18,
    path: "M6,20 L20,6 L34,20 M20,6 L20,0",
  },
];

function seededRandom(seed: number) {
  let s = seed;
  return () => {
    s = (s * 16807 + 0) % 2147483647;
    return (s - 1) / 2147483646;
  };
}

function tokenizeSiteNames(names: string[]): string[] {
  const tokens = new Set<string>();
  for (const name of names) {
    for (const token of name.split(/[\s_-]+/)) {
      const trimmed = token.trim();
      if (trimmed.length > 0) tokens.add(trimmed);
    }
  }
  return Array.from(tokens);
}

function buildFloatingItems(texts: string[], count: number) {
  const rand = seededRandom(Date.now() % 100000);
  const picked = texts.sort(() => rand() - 0.5).slice(0, count);
  return picked.map((text) => ({
    text,
    top: `${rand() * 80 + 5}%`,
    left: `${rand() * 85 + 5}%`,
    size: 0.55 + rand() * 0.5,
    duration: 14 + rand() * 16,
    delay: -(rand() * 14),
    drift: DRIFT_CLASSES[Math.floor(rand() * DRIFT_CLASSES.length)],
    opacity: 0.1 + rand() * 0.15,
  }));
}

interface FloatingFormulasBgProps {
  itemCount?: number;
  className?: string;
}

export function FloatingFormulasBg({
  itemCount = 12,
  className,
}: FloatingFormulasBgProps) {
  const [siteTokens, setSiteTokens] = useState<string[]>([]);

  useEffect(() => {
    axios.get<string[]>("/sn/names")
      .then(({ data }) => {
        if (data.length > 0) {
          setSiteTokens(tokenizeSiteNames(data));
        }
      })
      .catch(() => {});
  }, []);

  const items = useMemo(
    () => buildFloatingItems([...FORMULAS, ...siteTokens], itemCount),
    [itemCount, siteTokens],
  );

  return (
    <div
      className={`absolute inset-0 pointer-events-none overflow-hidden ${className ?? ""}`}
      aria-hidden="true"
    >
      {BONDS.map((b, i) => (
        <svg
          key={`bond-${i}`}
          className={`ff-bond ${b.drift}`}
          width={b.size}
          height={b.size}
          viewBox={`0 0 ${b.size} ${b.size}`}
          style={{
            top: b.top, left: b.left,
            animationDuration: `${b.duration}s`,
            animationDelay: `${b.delay}s`,
            opacity: b.opacity,
          }}
        >
          <path
            d={b.path} fill="none"
            className="ff-bond-line"
            strokeWidth="1.5" strokeLinecap="round"
          />
          {b.path.match(/[ML]\s*(\d+),(\d+)/g)?.map((m, j) => {
            const coords = m.match(/(\d+),(\d+)/);
            if (!coords) return null;
            return (
              <circle
                key={j} cx={coords[1]} cy={coords[2]}
                r="2.5" className="ff-bond-node"
              />
            );
          })}
        </svg>
      ))}
      {items.map((item, i) => (
        <span
          key={`f-${i}`}
          className={`ff-term ${item.drift}`}
          style={{
            top: item.top, left: item.left,
            fontSize: `${item.size}rem`,
            animationDuration: `${item.duration}s`,
            animationDelay: `${item.delay}s`,
            opacity: item.opacity,
          }}
        >
          {item.text}
        </span>
      ))}
    </div>
  );
}
