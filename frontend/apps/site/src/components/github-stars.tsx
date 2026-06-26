import { useEffect, useState } from "react";
import { REPO } from "@/components/brand";

/** Live GitHub star count, fetched once on mount. Falls back to null on error. */
export function useGitHubStars(repo: string = REPO) {
  const [stars, setStars] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    fetch(`https://api.github.com/repos/${repo}`)
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => {
        if (active && data && typeof data.stargazers_count === "number") {
          setStars(data.stargazers_count);
        }
      })
      .catch(() => {
        /* offline / rate-limited — badge just omits the count */
      });
    return () => {
      active = false;
    };
  }, [repo]);

  return stars;
}

export function formatStars(n: number): string {
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, "") + "k";
  return String(n);
}
