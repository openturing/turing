import { FloatingFormulasBg as DSFloatingFormulasBg, type FloatingFormulasBgProps as DSProps } from "@viglet/viglet-design-system";
import axios from "axios";
import { useEffect, useState } from "react";

/**
 * Turing-specific wrapper around the design-system `FloatingFormulasBg`.
 *
 * Two product concerns live here (so the base DS component can stay generic):
 *
 *   1. Fetches `/sn/names` once on mount and feeds the site names into the
 *      component as `extraTokens`, so the animated background blends real
 *      Turing SN site names with the scientific formula pool.
 *   2. Applies the *compact* preset (fewer items, no lightning/orbs/grid) used
 *      by Turing's card-level hero areas (exchange import page, SN export
 *      dialog). For the full-screen login/setup hero, use `<Login.Background>`
 *      / `<StartupFirst.Background>` directly from the DS.
 */
type Props = Omit<DSProps, "extraTokens" | "color" | "colorDark">;

export function FloatingFormulasBg({
  itemCount = 12,
  withFormulas = true,
  withBonds = true,
  withOrbs = false,
  withLightning = false,
  withExplosion = false,
  withGrid = false,
  className,
}: Readonly<Props>) {
  const [siteNames, setSiteNames] = useState<string[] | undefined>(undefined);

  useEffect(() => {
    axios.get<string[]>("/sn/names")
      .then(({ data }) => {
        if (data.length > 0) setSiteNames(data);
      })
      .catch(() => { /* ignore — fall back to formulas only */ });
  }, []);

  return (
    <DSFloatingFormulasBg
      itemCount={itemCount}
      extraTokens={siteNames}
      withFormulas={withFormulas}
      withBonds={withBonds}
      withOrbs={withOrbs}
      withLightning={withLightning}
      withExplosion={withExplosion}
      withGrid={withGrid}
      className={className}
    />
  );
}
