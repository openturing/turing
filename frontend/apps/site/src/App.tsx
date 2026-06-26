import { SiteNav } from "@/components/sections/site-nav";
import { Hero } from "@/components/sections/hero";
import { Providers } from "@/components/sections/providers";
import { Features } from "@/components/sections/features";
import { HowItWorks } from "@/components/sections/how-it-works";
import { CodeShowcase } from "@/components/sections/code-showcase";
import { Cli } from "@/components/sections/cli";
import { Stats } from "@/components/sections/stats";
import { Marketplace } from "@/components/sections/marketplace";
import { Resources } from "@/components/sections/resources";
import { CallToAction } from "@/components/sections/cta";
import { SiteFooter } from "@/components/sections/site-footer";

export default function App() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <Hero />
        <Providers />
        <Features />
        <HowItWorks />
        <CodeShowcase />
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
