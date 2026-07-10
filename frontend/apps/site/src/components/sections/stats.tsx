import { Star } from "lucide-react";
import { Container, GitHubIcon, LINKS } from "@/components/brand";
import { formatStars, useGitHubStars } from "@/components/github-stars";

/**
 * Proof strip — real, verifiable signals instead of vanity spec-counts.
 * The star count is fetched live from the GitHub API (null while loading /
 * rate-limited, in which case the tile shows "Apache 2.0" as a stable fallback).
 */
const FACTS: { value: string; label: string }[] = [
  { value: "Apache 2.0", label: "Open source — free forever" },
  { value: "Any LLM", label: "OpenAI · Anthropic · Gemini · Ollama" },
  { value: "3 engines", label: "Solr · Elasticsearch · Lucene" },
];

export function Stats() {
  const stars = useGitHubStars();

  return (
    <section className="bg-muted/40 py-12">
      <Container>
        <div className="grid grid-cols-2 gap-6 text-center sm:grid-cols-4">
          <a
            href={LINKS.github}
            target="_blank"
            rel="noreferrer"
            className="group"
            title="Star Turing on GitHub"
          >
            <div className="flex items-center justify-center gap-1.5 bg-gradient-to-r from-[#4169E1] to-[#818cf8] bg-clip-text text-3xl font-extrabold text-transparent">
              {stars !== null ? (
                <>
                  <Star className="size-6 fill-[#4169E1] text-[#4169E1]" />
                  {formatStars(stars)}
                </>
              ) : (
                "Apache 2.0"
              )}
            </div>
            <div className="mt-1 inline-flex items-center gap-1 text-sm font-semibold text-muted-foreground group-hover:text-foreground">
              <GitHubIcon className="size-3.5" />
              {stars !== null ? "GitHub stars" : "Open source"}
            </div>
          </a>
          {FACTS.map((s) => (
            <div key={s.label}>
              <div className="bg-gradient-to-r from-[#4169E1] to-[#818cf8] bg-clip-text text-3xl font-extrabold text-transparent">
                {s.value}
              </div>
              <div className="mt-1 text-sm font-semibold text-muted-foreground">
                {s.label}
              </div>
            </div>
          ))}
        </div>
      </Container>
    </section>
  );
}
