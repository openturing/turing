import { Container, SectionHeader } from "@/components/brand";
import { LinkCards } from "@/components/sections/link-cards";
import { RESOURCES } from "@/lib/site-content";

export function Resources() {
  return (
    <section className="bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="Ecosystem"
          title="SDKs, tools & documentation"
          description="Everything you need to build, integrate and ship with Turing."
        />
        <LinkCards items={RESOURCES} />
      </Container>
    </section>
  );
}
