import { SiteNav } from "@/components/sections/site-nav";
import { Hero } from "@/components/sections/hero";
import { Playground } from "@/components/sections/playground";
import { Providers } from "@/components/sections/providers";
import { Pillars } from "@/components/sections/pillars";
import { Outcomes } from "@/components/sections/outcomes";
import { Audiences } from "@/components/sections/audiences";
import { NativeTools } from "@/components/sections/native-tools";
import { Comparison } from "@/components/sections/comparison";
import { HowItWorks } from "@/components/sections/how-it-works";
import { LiveDemo } from "@/components/sections/live-demo";
import { SelfHost } from "@/components/sections/self-host";
import { Cli } from "@/components/sections/cli";
import { Stats } from "@/components/sections/stats";
import { Marketplace } from "@/components/sections/marketplace";
import { Resources } from "@/components/sections/resources";
import { CallToAction } from "@/components/sections/cta";
import { SiteFooter } from "@/components/sections/site-footer";

/**
 * The single-page landing — the marketing front door.
 *
 * Section order is a deliberate conversion narrative, built around the live
 * playground as the centerpiece proof:
 * hook → SEE it work (live search+chat over one corpus) → the code behind it →
 * what it is → outcomes it drives → works with your stack → who it's for →
 * proof → how → why vs alternatives → run it yourself → advanced → dev workflow
 * → starting points → ecosystem → convert.
 */
export function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <Hero />
        <Playground />
        <LiveDemo />
        <Pillars />
        <Outcomes />
        <Providers />
        <Audiences />
        <Stats />
        <HowItWorks />
        <Comparison />
        <SelfHost />
        <NativeTools teaser />
        <Cli />
        <Marketplace />
        <Resources />
        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
