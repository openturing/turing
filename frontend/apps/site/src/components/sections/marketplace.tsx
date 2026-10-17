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
          description="Example sites with search templates. Import a manifest and explore."
        />
        <LinkCards items={MARKETPLACE} />
      </Container>
    </section>
  );
}
