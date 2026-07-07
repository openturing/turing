import { IconStar, IconStarFilled, IconStarHalfFilled } from "@tabler/icons-react";
import { toStars } from "@/lib/format";

/** A 0–5 star rating rendered from a numeric value (rounds to nearest half). */
export function Stars({
  rating,
  className = "size-3.5",
}: Readonly<{ rating: number | undefined; className?: string }>) {
  const { full, half } = toStars(rating);
  const slots = ["s1", "s2", "s3", "s4", "s5"];
  return (
    <span className="flex items-center gap-0.5 text-amber-500">
      {slots.map((slot, i) => {
        let Icon = IconStar;
        if (i < full) Icon = IconStarFilled;
        else if (i === full && half) Icon = IconStarHalfFilled;
        const muted = i >= full && !(i === full && half);
        return (
          <Icon key={slot} className={muted ? `${className} text-muted-foreground/40` : className} />
        );
      })}
    </span>
  );
}
