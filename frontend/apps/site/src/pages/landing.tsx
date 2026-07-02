import { SiteNav } from "@/components/sections/site-nav";
import { Hero } from "@/components/sections/hero";
import { Providers } from "@/components/sections/providers";
import { Pillars } from "@/components/sections/pillars";
import { NativeTools } from "@/components/sections/native-tools";
import { Comparison } from "@/components/sections/comparison";
import { HowItWorks } from "@/components/sections/how-it-works";
import { LiveDemo } from "@/components/sections/live-demo";
import { Cli } from "@/components/sections/cli";
import { Stats } from "@/components/sections/stats";
import { Marketplace } from "@/components/sections/marketplace";
import { Resources } from "@/components/sections/resources";
import { CallToAction } from "@/components/sections/cta";
import { SiteFooter } from "@/components/sections/site-footer";

/** The single-page landing — the marketing front door. */
export function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <Hero />
        <Providers />
        <Pillars />
        <NativeTools teaser />
        <Comparison />
        <HowItWorks />
        <LiveDemo />
        <Cli />
        <Stats />
        <Marketplace />
        <Resources />
        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
