import { ModeToggle } from "@/components/mode-toggle";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import type { ResolvedDocument } from "@viglet/turing-react-sdk";
import {
  IconArrowLeft,
  IconBuilding,
  IconCalendar,
  IconCertificate,
  IconClock,
  IconCurrencyDollar,
  IconDisc,
  IconMusic,
  IconNumber,
  IconStar,
  IconStarFilled,
  IconTag,
  IconVinyl,
} from "@tabler/icons-react";
import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";

/* ── Condition color map ── */
const conditionColors: Record<string, string> = {
  Mint: "bg-emerald-500/20 text-emerald-300 border-emerald-500/30",
  "Near Mint": "bg-emerald-500/15 text-emerald-400 border-emerald-500/25",
  "Very Good Plus": "bg-blue-500/20 text-blue-300 border-blue-500/30",
  "Very Good": "bg-yellow-500/20 text-yellow-300 border-yellow-500/30",
  "Good Plus": "bg-orange-500/20 text-orange-300 border-orange-500/30",
  Good: "bg-orange-500/15 text-orange-400 border-orange-500/25",
  Fair: "bg-red-500/15 text-red-400 border-red-500/25",
};

function getConditionColor(condition: string): string {
  return (
    conditionColors[condition] ??
    "bg-amber-500/20 text-amber-300 border-amber-500/30"
  );
}

/* ── Star rating ── */
function StarRating({ rating }: Readonly<{ rating: number }>) {
  const stars = Math.round(rating);
  return (
    <div className="flex items-center gap-0.5">
      {Array.from({ length: 5 }, (_, i) =>
        i < stars ? (
          <IconStarFilled key={i} className="size-4 text-amber-400" />
        ) : (
          <IconStar key={i} className="size-4 text-muted-foreground/30" />
        )
      )}
      <span className="ml-1.5 text-sm text-muted-foreground font-mono">
        {rating}/5
      </span>
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
  const coverArt = fields.cover_art ?? doc.image;
  const artist = fields.artist ?? "";
  const genres = toArray(fields.genre);
  const condition = fields.condition ?? "";
  const format = fields.format ?? "";
  const price = fields.price;
  const rating = fields.rating ?? 0;
  const label = fields.label ?? "";
  const releaseDate = formatDate(fields.release_date ?? fields.date);
  const decade = fields.decade ?? "";
  const numTracks = fields.num_tracks ?? fields.tracks ?? "";
  const isFirstPressing =
    fields.is_first_pressing === true || fields.is_first_pressing === "true";

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
            Back to the Crate
          </Button>
          <ModeToggle />
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 sm:px-6 py-8">
        {/* ── Hero section with cover art ── */}
        <div className="relative mb-8 overflow-hidden rounded-2xl border border-border/30 bg-linear-to-br from-card via-card to-amber-900/5">
          <div className="flex flex-col md:flex-row">
            {/* Cover art */}
            <div className="relative md:w-96 shrink-0">
              {coverArt ? (
                <div className="relative aspect-square">
                  <img
                    src={coverArt}
                    alt={doc.title}
                    className="h-full w-full object-cover"
                  />
                  <div className="absolute inset-0 bg-linear-to-r from-transparent to-card/20 hidden md:block" />
                </div>
              ) : (
                <div className="flex aspect-square items-center justify-center bg-linear-to-br from-amber-900/20 to-orange-900/20">
                  <IconDisc className="size-24 text-muted-foreground/20" />
                </div>
              )}
            </div>

            {/* Title overlay / info */}
            <div className="flex flex-col justify-end p-6 sm:p-8 flex-1">
              {/* Genre badges */}
              <div className="flex flex-wrap gap-2 mb-3">
                {genres.map((g) => (
                  <Badge
                    key={g}
                    variant="secondary"
                    className="text-xs bg-amber-500/10 text-amber-300 border border-amber-500/20"
                  >
                    <IconTag className="size-3 mr-0.5" />
                    {g}
                  </Badge>
                ))}
                {isFirstPressing && (
                  <Badge
                    variant="outline"
                    className="text-xs bg-amber-600/20 text-amber-300 border-amber-500/30"
                  >
                    <IconCertificate className="size-3 mr-0.5" />
                    First Pressing
                  </Badge>
                )}
              </div>

              <h1 className="text-2xl sm:text-4xl font-extrabold tracking-tight leading-tight">
                {doc.title}
              </h1>

              {artist && (
                <p className="mt-2 text-lg text-primary flex items-center gap-2 font-medium">
                  <IconMusic className="size-5 shrink-0" />
                  {artist}
                </p>
              )}

              {/* Rating & Price hero row */}
              <div className="flex flex-wrap items-center gap-4 mt-4">
                {Number(rating) > 0 && (
                  <StarRating rating={Number(rating)} />
                )}
                {price != null && (
                  <span className="text-2xl font-bold text-primary">
                    ${price}
                  </span>
                )}
                {condition && (
                  <Badge
                    variant="outline"
                    className={cn("text-xs", getConditionColor(condition))}
                  >
                    {condition}
                  </Badge>
                )}
                {format && (
                  <Badge
                    variant="outline"
                    className="text-xs bg-purple-500/10 text-purple-300 border-purple-500/20"
                  >
                    <IconVinyl className="size-3 mr-0.5" />
                    {format}
                  </Badge>
                )}
              </div>

              {doc.description && (
                <p className="mt-4 text-sm text-muted-foreground max-w-2xl leading-relaxed"
                  dangerouslySetInnerHTML={{ __html: doc.description }}
                />
              )}
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 lg:gap-8">
          {/* ── Main content ── */}
          <div className="lg:col-span-2 space-y-8">
            {/* Full text / album story */}
            {doc.text && (
              <div className="space-y-3">
                <h2 className="text-lg font-semibold flex items-center gap-2">
                  <IconDisc className="size-5 text-primary" />
                  The Story
                </h2>
                <div className="text-sm leading-relaxed text-foreground/85 whitespace-pre-line rounded-xl border border-border/30 bg-card/50 p-5">
                  {doc.text}
                </div>
              </div>
            )}

          </div>

          {/* ── Sidebar metadata — record sleeve info panel ── */}
          <aside className="space-y-1 rounded-xl border border-border/40 bg-card/50 p-5 h-fit">
            <h2 className="text-sm font-bold uppercase tracking-widest text-primary/70 mb-2 flex items-center gap-1.5">
              <IconVinyl className="size-4" />
              Record Info
            </h2>

            {label && (
              <MetaRow icon={<IconBuilding className="size-4" />} label="Label">
                {label}
              </MetaRow>
            )}

            {releaseDate && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconCalendar className="size-4" />}
                  label="Release Date"
                >
                  {releaseDate}
                </MetaRow>
              </>
            )}

            {decade && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconClock className="size-4" />}
                  label="Decade"
                >
                  {decade}
                </MetaRow>
              </>
            )}

            {format && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconDisc className="size-4" />}
                  label="Format"
                >
                  {format}
                </MetaRow>
              </>
            )}

            {condition && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconStar className="size-4" />}
                  label="Condition"
                >
                  <Badge
                    variant="outline"
                    className={cn("text-xs", getConditionColor(condition))}
                  >
                    {condition}
                  </Badge>
                </MetaRow>
              </>
            )}

            {price != null && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconCurrencyDollar className="size-4" />}
                  label="Price"
                >
                  <span className="text-lg font-bold text-primary">
                    ${price}
                  </span>
                </MetaRow>
              </>
            )}

            {Number(rating) > 0 && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconStarFilled className="size-4" />}
                  label="Rating"
                >
                  <StarRating rating={Number(rating)} />
                </MetaRow>
              </>
            )}

            {numTracks && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconNumber className="size-4" />}
                  label="Tracks"
                >
                  {numTracks}
                </MetaRow>
              </>
            )}

            {/* First pressing indicator */}
            <Separator />
            <div className="flex items-center gap-4 py-3">
              <div
                className={cn(
                  "flex items-center gap-1.5 text-sm",
                  isFirstPressing
                    ? "text-amber-400"
                    : "text-muted-foreground/50"
                )}
              >
                <IconCertificate className="size-4" />
                <span>
                  {isFirstPressing ? "First Pressing" : "Reissue / Repress"}
                </span>
              </div>
            </div>

            {/* Genres in sidebar */}
            {genres.length > 0 && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconTag className="size-4" />}
                  label="Genre"
                >
                  <div className="flex flex-wrap gap-1.5">
                    {genres.map((g) => (
                      <Badge
                        key={g}
                        variant="secondary"
                        className="text-xs bg-amber-500/10 text-amber-300 border border-amber-500/20"
                      >
                        {g}
                      </Badge>
                    ))}
                  </div>
                </MetaRow>
              </>
            )}
          </aside>
        </div>
      </main>

      {/* ── Footer ── */}
      <footer className="border-t border-border/30 bg-card/30 mt-12">
        <div className="mx-auto max-w-5xl px-4 sm:px-6 py-6 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-muted-foreground">
          <span>Powered by Viglet Turing ES</span>
          <span className="flex items-center gap-1.5">
            <IconVinyl className="size-3.5" />
            The Crate — Vinyl Records
          </span>
        </div>
      </footer>
    </div>
  );
}
