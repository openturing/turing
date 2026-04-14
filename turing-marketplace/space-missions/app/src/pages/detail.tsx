import { ModeToggle } from "@/components/mode-toggle";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import type { ResolvedDocument } from "@viglet/turing-react-sdk";
import {
  IconArrowLeft,
  IconBuildingFactory,
  IconCalendar,
  IconClock,
  IconPlanet,
  IconRocket,
  IconSatellite,
  IconStars,
  IconUsers,
  IconWalk,
} from "@tabler/icons-react";
import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";

/* ── Outcome color map ── */
const outcomeColors: Record<string, string> = {
  Success: "bg-emerald-500/20 text-emerald-300 border-emerald-500/40",
  Failure: "bg-red-500/20 text-red-300 border-red-500/40",
  Partial: "bg-amber-500/20 text-amber-300 border-amber-500/40",
  "Partial Success": "bg-amber-500/20 text-amber-300 border-amber-500/40",
  Ongoing: "bg-cyan-500/20 text-cyan-300 border-cyan-500/40",
  Planned: "bg-indigo-500/20 text-indigo-300 border-indigo-500/40",
};

function getOutcomeColor(outcome: string): string {
  return (
    outcomeColors[outcome] ??
    "bg-slate-500/20 text-slate-300 border-slate-500/40"
  );
}

/* ── Agency color map ── */
const agencyColors: Record<string, string> = {
  NASA: "bg-blue-500/20 text-blue-300 border-blue-500/40",
  ESA: "bg-sky-500/20 text-sky-300 border-sky-500/40",
  Roscosmos: "bg-red-500/20 text-red-300 border-red-500/40",
  SpaceX: "bg-slate-500/20 text-slate-200 border-slate-500/40",
  JAXA: "bg-rose-500/20 text-rose-300 border-rose-500/40",
  ISRO: "bg-orange-500/20 text-orange-300 border-orange-500/40",
  CNSA: "bg-yellow-500/20 text-yellow-300 border-yellow-500/40",
};

function getAgencyColor(agency: string): string {
  return (
    agencyColors[agency] ??
    "bg-violet-500/20 text-violet-300 border-violet-500/40"
  );
}

/* ── Metadata row ── */
type MetaRowProps = Readonly<{
  icon: React.ReactNode;
  label: string;
  children: React.ReactNode;
}>;

function MetaRow({
  icon,
  label,
  children,
}: MetaRowProps) {
  return (
    <div className="flex items-start gap-3 py-3">
      <div className="mt-0.5 text-primary/60 shrink-0">{icon}</div>
      <div className="min-w-0">
        <div className="text-[10px] font-bold uppercase tracking-[0.15em] text-muted-foreground mb-1 font-mono">
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

function formatDate(value: unknown): string {
  if (!value) return "";
  const date =
    typeof value === "number"
      ? new Date(value)
      : new Date(String(value));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleDateString("en-US", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
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
  const agencies = toArray(fields.agency);
  const destinations = toArray(fields.destination);
  const crew = toArray(fields.crew);
  const outcome = (fields.outcome as string) ?? "";
  const program = (fields.program as string) ?? "";
  const spacecraft = (fields.spacecraft as string) ?? "";
  const launchDate = formatDate(fields.launch_date ?? fields.date);
  const duration = (fields.duration as string) ?? "";
  const crewSize = (fields.crew_size as number) ?? 0;
  const hasEva = fields.eva === true || fields.eva === "true";

  return (
    <div className="min-h-screen flex flex-col">
      {/* ── Header ── */}
      <header className="sticky top-0 z-40 border-b border-border/30 bg-background/80 backdrop-blur-xl">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4 sm:px-6">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate("/")}
            className="gap-1.5 text-muted-foreground hover:text-foreground font-mono text-xs"
          >
            <IconArrowLeft className="size-4" />
            Back to Mission Control
          </Button>
          <ModeToggle />
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 sm:px-6 py-8">
        {/* ── Hero image ── */}
        {doc.image && (
          <div className="relative mb-8 overflow-hidden rounded-2xl border border-border/30 bg-muted/10">
            <div className="relative h-64 sm:h-80 lg:h-96">
              <img
                src={doc.image}
                alt={doc.title}
                className="h-full w-full object-cover"
              />
              <div className="absolute inset-0 bg-linear-to-t from-background via-background/50 to-transparent" />

              {/* HUD corner brackets */}
              <div className="absolute top-3 left-3 w-6 h-6 border-t-2 border-l-2 border-primary/40 rounded-tl-lg pointer-events-none" />
              <div className="absolute top-3 right-3 w-6 h-6 border-t-2 border-r-2 border-primary/40 rounded-tr-lg pointer-events-none" />
              <div className="absolute bottom-16 left-3 w-6 h-6 border-b-2 border-l-2 border-primary/40 rounded-bl-lg pointer-events-none" />
              <div className="absolute bottom-16 right-3 w-6 h-6 border-b-2 border-r-2 border-primary/40 rounded-br-lg pointer-events-none" />
            </div>

            {/* Title overlay */}
            <div className="absolute bottom-0 left-0 right-0 p-6 sm:p-8">
              {/* Badges */}
              <div className="flex flex-wrap gap-2 mb-3">
                {outcome && (
                  <Badge
                    variant="outline"
                    className={cn(
                      "text-xs font-mono backdrop-blur-sm",
                      getOutcomeColor(outcome)
                    )}
                  >
                    {outcome}
                  </Badge>
                )}
                {agencies.map((a) => (
                  <Badge
                    key={a}
                    variant="outline"
                    className={cn(
                      "text-xs font-mono backdrop-blur-sm",
                      getAgencyColor(a)
                    )}
                  >
                    {a}
                  </Badge>
                ))}
              </div>

              <h1 className="text-2xl sm:text-4xl font-extrabold tracking-tighter leading-tight font-mono uppercase">
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
              {outcome && (
                <Badge
                  variant="outline"
                  className={cn(
                    "text-xs font-mono",
                    getOutcomeColor(outcome)
                  )}
                >
                  {outcome}
                </Badge>
              )}
              {agencies.map((a) => (
                <Badge
                  key={a}
                  variant="outline"
                  className={cn("text-xs font-mono", getAgencyColor(a))}
                >
                  {a}
                </Badge>
              ))}
            </div>
            <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tighter font-mono uppercase">
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
            {/* Full text */}
            {doc.text && (
              <div className="space-y-3">
                <h2 className="text-lg font-bold flex items-center gap-2 font-mono uppercase tracking-wider">
                  <IconStars className="size-5 text-primary" />
                  Mission Brief
                </h2>
                <div className="text-sm leading-relaxed text-foreground/85 whitespace-pre-line rounded-xl border border-border/30 bg-card/40 backdrop-blur-sm p-5">
                  {doc.text}
                </div>
              </div>
            )}

            {/* Crew manifest */}
            {crew.length > 0 && (
              <div className="space-y-3">
                <h2 className="text-lg font-bold flex items-center gap-2 font-mono uppercase tracking-wider">
                  <IconUsers className="size-5 text-primary" />
                  Crew Manifest
                </h2>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                  {crew.map((member) => (
                    <div
                      key={member}
                      className="flex items-center gap-2 rounded-lg border border-border/30 bg-card/40 backdrop-blur-sm px-3 py-2.5 text-sm font-mono"
                    >
                      <div className="size-7 rounded-full bg-primary/10 border border-primary/30 flex items-center justify-center text-primary text-xs font-bold shrink-0">
                        {member.charAt(0)}
                      </div>
                      <span className="truncate text-xs">{member}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

          </div>

          {/* ── Sidebar metadata ── */}
          <aside className="space-y-1 rounded-xl border border-border/30 bg-card/40 backdrop-blur-sm p-5 h-fit">
            <h2 className="text-[10px] font-bold uppercase tracking-[0.2em] text-primary/70 mb-3 font-mono">
              Mission Telemetry
            </h2>

            {launchDate && (
              <MetaRow
                icon={<IconCalendar className="size-4" />}
                label="Launch Date"
              >
                <span className="font-mono">{launchDate}</span>
              </MetaRow>
            )}

            {duration && (
              <>
                <Separator className="bg-border/30" />
                <MetaRow
                  icon={<IconClock className="size-4" />}
                  label="Duration"
                >
                  <span className="font-mono">{duration}</span>
                </MetaRow>
              </>
            )}

            {spacecraft && (
              <>
                <Separator className="bg-border/30" />
                <MetaRow
                  icon={<IconRocket className="size-4" />}
                  label="Spacecraft"
                >
                  <span className="font-mono">{spacecraft}</span>
                </MetaRow>
              </>
            )}

            {outcome && (
              <>
                <Separator className="bg-border/30" />
                <MetaRow
                  icon={<IconSatellite className="size-4" />}
                  label="Outcome"
                >
                  <Badge
                    variant="outline"
                    className={cn(
                      "text-xs font-mono",
                      getOutcomeColor(outcome)
                    )}
                  >
                    {outcome}
                  </Badge>
                </MetaRow>
              </>
            )}

            {destinations.length > 0 && (
              <>
                <Separator className="bg-border/30" />
                <MetaRow
                  icon={<IconPlanet className="size-4" />}
                  label="Destination"
                >
                  <div className="flex flex-wrap gap-1.5">
                    {destinations.map((d) => (
                      <Badge
                        key={d}
                        variant="outline"
                        className="text-xs font-mono border-primary/30 text-primary/80"
                      >
                        {d}
                      </Badge>
                    ))}
                  </div>
                </MetaRow>
              </>
            )}

            {agencies.length > 0 && (
              <>
                <Separator className="bg-border/30" />
                <MetaRow
                  icon={<IconBuildingFactory className="size-4" />}
                  label="Agency"
                >
                  <div className="flex flex-wrap gap-1.5">
                    {agencies.map((a) => (
                      <Badge
                        key={a}
                        variant="outline"
                        className={cn("text-xs font-mono", getAgencyColor(a))}
                      >
                        {a}
                      </Badge>
                    ))}
                  </div>
                </MetaRow>
              </>
            )}

            {program && (
              <>
                <Separator className="bg-border/30" />
                <MetaRow
                  icon={<IconStars className="size-4" />}
                  label="Program"
                >
                  <span className="font-mono">{program}</span>
                </MetaRow>
              </>
            )}

            {crewSize > 0 && (
              <>
                <Separator className="bg-border/30" />
                <MetaRow
                  icon={<IconUsers className="size-4" />}
                  label="Crew Size"
                >
                  <span className="font-mono font-bold">{crewSize}</span>{" "}
                  <span className="text-muted-foreground text-xs">
                    astronaut{crewSize === 1 ? "" : "s"}
                  </span>
                </MetaRow>
              </>
            )}

            {/* EVA indicator */}
            <Separator className="bg-border/30" />
            <div className="flex items-center gap-3 py-3">
              <div
                className={cn(
                  "flex items-center gap-1.5 text-sm font-mono",
                  hasEva ? "text-cyan-400" : "text-muted-foreground/50"
                )}
              >
                <IconWalk className="size-4" />
                <span>{hasEva ? "EVA Performed" : "No EVA"}</span>
              </div>
            </div>
          </aside>
        </div>
      </main>

      {/* ── Footer ── */}
      <footer className="border-t border-border/20 bg-card/20 backdrop-blur-sm mt-12">
        <div className="mx-auto max-w-5xl px-4 sm:px-6 py-6 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-muted-foreground font-mono">
          <span>Powered by Viglet Turing ES</span>
          <span className="flex items-center gap-1.5">
            <IconSatellite className="size-3" />
            Space Missions Database
          </span>
        </div>
      </footer>
    </div>
  );
}
