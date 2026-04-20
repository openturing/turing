import { ModeToggle } from "@/components/mode-toggle";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import type { ResolvedDocument } from "@viglet/turing-react-sdk";
import {
  IconArrowLeft,
  IconClock,
  IconFeather,
  IconFlame,
  IconGhost,
  IconInfinity,
  IconMapPin,
  IconShieldHalf,
  IconSkull,
  IconSparkles,
  IconSword,
  IconTransform,
} from "@tabler/icons-react";
import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import "./detail.css";

/* ── Element color map ── */
const elementColors: Record<string, string> = {
  Fire: "bg-orange-500/20 text-orange-300 border-orange-500/30",
  Water: "bg-cyan-500/20 text-cyan-300 border-cyan-500/30",
  Earth: "bg-emerald-500/20 text-emerald-300 border-emerald-500/30",
  Air: "bg-sky-500/20 text-sky-300 border-sky-500/30",
  Lightning: "bg-yellow-500/20 text-yellow-300 border-yellow-500/30",
  Shadow: "bg-violet-500/20 text-violet-300 border-violet-500/30",
  Spirit: "bg-indigo-500/20 text-indigo-300 border-indigo-500/30",
  Ice: "bg-blue-300/20 text-blue-200 border-blue-300/30",
  Poison: "bg-lime-500/20 text-lime-300 border-lime-500/30",
};

function getElementColor(element: string): string {
  return elementColors[element] ?? "bg-purple-500/20 text-purple-300 border-purple-500/30";
}

/* ── Danger bar ── */
function DangerBar({ level }: Readonly<{ level: number }>) {
  const clampedLevel = Math.max(0, Math.min(Math.round(level), 10));

  let color: string;
  if (level >= 8) {
    color = "from-red-600 to-red-400";
  } else if (level >= 5) {
    color = "from-amber-600 to-amber-400";
  } else {
    color = "from-emerald-600 to-emerald-400";
  }

  let label: string;
  if (level >= 9) {
    label = "Catastrophic";
  } else if (level >= 7) {
    label = "Deadly";
  } else if (level >= 5) {
    label = "Dangerous";
  } else if (level >= 3) {
    label = "Moderate";
  } else {
    label = "Low";
  }

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-sm">
        <span className="text-muted-foreground flex items-center gap-1.5">
          <IconSkull className="size-4" />
          Danger Level
        </span>
        <span className="font-mono font-semibold">
          {level}/10 &mdash; {label}
        </span>
      </div>
      <div className="h-2.5 rounded-full bg-muted/50 overflow-hidden">
        <div
          data-level={clampedLevel}
          className={cn(
            "danger-bar-fill h-full rounded-full bg-linear-to-r transition-all duration-700",
            color
          )}
        />
      </div>
    </div>
  );
}

/* ── Metadata row ── */
function MetaRow({
  icon,
  label,
  children,
}: Readonly<{
  icon: React.ReactNode;
  label: string;
  children: React.ReactNode;
}>) {
  return (
    <div className="flex items-start gap-3 py-3">
      <div className="mt-0.5 text-muted-foreground shrink-0">{icon}</div>
      <div className="min-w-0">
        <div className="text-xs font-medium uppercase tracking-wider text-muted-foreground mb-1">
          {label}
        </div>
        <div className="text-sm">{children}</div>
      </div>
    </div>
  );
}

function toArray(value: unknown): string[] {
  if (Array.isArray(value)) return value;
  if (typeof value === "string" && value) return [value];
  return [];
}

export default function DetailPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const doc = location.state?.document as ResolvedDocument | undefined;

  useEffect(() => {
    if (!doc) navigate("/", { replace: true });
  }, [doc, navigate]);

  if (!doc) return null;

  const fields = doc.raw.fields;
  const origins = toArray(fields.origin);
  const creatureTypes = toArray(fields.creature_type);
  const elements = toArray(fields.element);
  const abilities = toArray(fields.abilities);
  const weaknesses = toArray(fields.weakness);
  const dangerLevel = fields.danger_level ?? 0;
  const habitat = fields.habitat ?? "";
  const era = fields.era ?? "";
  const isShapeshifter = fields.is_shapeshifter === true || fields.is_shapeshifter === "true";
  const isImmortal = fields.is_immortal === true || fields.is_immortal === "true";

  return (
    <div className="min-h-screen flex flex-col">
      {/* ── Header ── */}
      <header className="sticky top-0 z-40 border-b border-border/40 bg-background/80 backdrop-blur-xl">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4 sm:px-6">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate(-1)}
            className="gap-1.5 text-muted-foreground hover:text-foreground"
          >
            <IconArrowLeft className="size-4" />
            Back to Bestiary
          </Button>
          <ModeToggle />
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 sm:px-6 py-8">
        {/* ── Hero image ── */}
        {doc.image && (
          <div className="relative mb-8 overflow-hidden rounded-2xl border border-border/30 bg-muted/20">
            <div className="relative h-64 sm:h-80 lg:h-96">
              <img
                src={doc.image}
                alt={doc.title}
                className="h-full w-full object-cover"
              />
              <div className="absolute inset-0 bg-linear-to-t from-background via-background/40 to-transparent" />
            </div>

            {/* Title overlay */}
            <div className="absolute bottom-0 left-0 right-0 p-6 sm:p-8">
              {/* Element badges */}
              <div className="flex flex-wrap gap-2 mb-3">
                {elements.map((el) => (
                  <Badge
                    key={el}
                    variant="outline"
                    className={cn(
                      "text-xs backdrop-blur-sm",
                      getElementColor(el)
                    )}
                  >
                    {el}
                  </Badge>
                ))}
                {creatureTypes.map((ct) => (
                  <Badge
                    key={ct}
                    variant="secondary"
                    className="text-xs backdrop-blur-sm bg-secondary/60"
                  >
                    <IconFeather className="size-3 mr-0.5" />
                    {ct}
                  </Badge>
                ))}
              </div>

              <h1 className="text-2xl sm:text-4xl font-extrabold tracking-tight leading-tight">
                {doc.title}
              </h1>

              {doc.description && (
                <p className="mt-2 text-sm sm:text-base text-muted-foreground max-w-2xl leading-relaxed"
                  dangerouslySetInnerHTML={{ __html: doc.description }}
                />
              )}
            </div>
          </div>
        )}

        {/* ── No image fallback title ── */}
        {!doc.image && (
          <div className="mb-8">
            <div className="flex flex-wrap gap-2 mb-3">
              {elements.map((el) => (
                <Badge
                  key={el}
                  variant="outline"
                  className={cn("text-xs", getElementColor(el))}
                >
                  {el}
                </Badge>
              ))}
            </div>
            <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight">
              {doc.title}
            </h1>
            {doc.description && (
              <p className="mt-3 text-muted-foreground max-w-2xl leading-relaxed"
                dangerouslySetInnerHTML={{ __html: doc.description }}
              />
            )}
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 lg:gap-8">
          {/* ── Main content ── */}
          <div className="lg:col-span-2 space-y-8">
            {/* Danger bar */}
            {dangerLevel > 0 && (
              <div className="rounded-xl border border-border/40 bg-card/50 p-5">
                <DangerBar level={dangerLevel} />
              </div>
            )}

            {/* Full text */}
            {doc.text && (
              <div className="space-y-3">
                <h2 className="text-lg font-semibold flex items-center gap-2">
                  <IconSparkles className="size-5 text-primary" />
                  Lore
                </h2>
                <div className="text-sm leading-relaxed text-foreground/85 whitespace-pre-line">
                  {doc.text}
                </div>
              </div>
            )}

          </div>

          {/* ── Sidebar metadata ── */}
          <aside className="space-y-1 rounded-xl border border-border/40 bg-card/50 p-5 h-fit">
            <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground mb-2">
              Creature Data
            </h2>

            {origins.length > 0 && (
              <MetaRow icon={<IconMapPin className="size-4" />} label="Origin">
                <div className="flex flex-wrap gap-1.5">
                  {origins.map((o) => (
                    <Badge key={o} variant="outline" className="text-xs">
                      {o}
                    </Badge>
                  ))}
                </div>
              </MetaRow>
            )}

            {habitat && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconGhost className="size-4" />}
                  label="Habitat"
                >
                  {habitat}
                </MetaRow>
              </>
            )}

            {era && (
              <>
                <Separator />
                <MetaRow icon={<IconClock className="size-4" />} label="Era">
                  {era}
                </MetaRow>
              </>
            )}

            {abilities.length > 0 && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconFlame className="size-4" />}
                  label="Abilities"
                >
                  <div className="flex flex-wrap gap-1.5">
                    {abilities.map((a) => (
                      <Badge
                        key={a}
                        variant="secondary"
                        className="text-xs bg-amber-500/10 text-amber-400 border border-amber-500/20"
                      >
                        {a}
                      </Badge>
                    ))}
                  </div>
                </MetaRow>
              </>
            )}

            {weaknesses.length > 0 && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconSword className="size-4" />}
                  label="Weaknesses"
                >
                  <div className="flex flex-wrap gap-1.5">
                    {weaknesses.map((w) => (
                      <Badge
                        key={w}
                        variant="outline"
                        className="text-xs border-red-500/30 text-red-400"
                      >
                        {w}
                      </Badge>
                    ))}
                  </div>
                </MetaRow>
              </>
            )}

            {/* Boolean traits */}
            <Separator />
            <div className="flex items-center gap-4 py-3">
              <div
                className={cn(
                  "flex items-center gap-1.5 text-sm",
                  isShapeshifter
                    ? "text-amber-400"
                    : "text-muted-foreground/50"
                )}
              >
                <IconTransform className="size-4" />
                <span>{isShapeshifter ? "Shapeshifter" : "Not a Shapeshifter"}</span>
              </div>
            </div>
            <div className="flex items-center gap-4 pb-1">
              <div
                className={cn(
                  "flex items-center gap-1.5 text-sm",
                  isImmortal
                    ? "text-purple-400"
                    : "text-muted-foreground/50"
                )}
              >
                <IconInfinity className="size-4" />
                <span>{isImmortal ? "Immortal" : "Mortal"}</span>
              </div>
            </div>

            {/* Shield / defense hint */}
            {dangerLevel >= 8 && (
              <>
                <Separator />
                <div className="flex items-center gap-2 py-3 text-xs text-red-400/80">
                  <IconShieldHalf className="size-4 shrink-0" />
                  <span>
                    Extreme danger. Approach only with divine protection.
                  </span>
                </div>
              </>
            )}
          </aside>
        </div>
      </main>

      {/* ── Footer ── */}
      <footer className="border-t border-border/30 bg-card/30 mt-12">
        <div className="mx-auto max-w-5xl px-4 sm:px-6 py-6 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-muted-foreground">
          <span>Powered by Viglet Turing ES</span>
          <span>Mythical Creatures Bestiary</span>
        </div>
      </footer>
    </div>
  );
}
