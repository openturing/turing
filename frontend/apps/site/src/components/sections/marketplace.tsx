import { Container, SectionHeader } from "@/components/brand";
import { LinkCards } from "@/components/sections/link-cards";
import { MARKETPLACE } from "@/lib/site-content";

export function Marketplace() {
  return (
    <section id="marketplace" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="Marketplace"
          title="Start from a working example"
          description="Ready-to-use example sites with search templates — import a manifest and explore in minutes."
        />
        <LinkCards items={MARKETPLACE} />
      </Container>
    </section>
  );
}
