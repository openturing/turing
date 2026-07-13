import { Container, Logo, LINKS } from "@/components/brand";

const COLUMNS: { heading: string; links: { label: string; href: string }[] }[] = [
  {
    heading: "Product",
    links: [
      // Root-anchored so they also work from the /features/* pages (match SiteNav).
      { label: "Capabilities", href: "/#features" },
      { label: "How it works", href: "/#how" },
      { label: "Compare", href: "/compare" },
      { label: "Migrate", href: "/migrate" },
      { label: "AEM search", href: "/aem-search" },
      { label: "Security", href: "/security" },
      { label: "Self-host", href: "/#self-host" },
      { label: "Marketplace", href: "/#marketplace" },
      { label: "React SDK", href: LINKS.storybook },
    ],
  },
  {
    heading: "Developers",
    links: [
      { label: "Documentation", href: LINKS.docs },
      { label: "npm", href: LINKS.npm },
      { label: "CLI", href: LINKS.cli },
    ],
  },
  {
    heading: "Open & transparent",
    links: [
      { label: "Roadmap", href: LINKS.roadmap },
      { label: "Changelog", href: LINKS.changelog },
      { label: "viglet.org", href: LINKS.viglet },
    ],
  },
];

export function SiteFooter() {
  return (
    <footer className="bg-[#07080f] text-[#8b94b3]">
      <Container className="py-14">
        <div className="grid gap-8 sm:grid-cols-2 md:grid-cols-[1.4fr_1fr_1fr_1fr]">
          <div>
            <Logo className="text-[#f2f4fb]" />
            <p className="mt-3 max-w-xs text-sm leading-relaxed">
              Open-source Enterprise Search Intelligence Platform — agents, tools,
              skills, MCP and RAG for your content.
            </p>
          </div>
          {COLUMNS.map((col) => (
            <div key={col.heading}>
              <h3 className="text-xs font-bold tracking-[0.06em] text-[#f2f4fb] uppercase">
                {col.heading}
              </h3>
              <ul className="mt-4 space-y-2.5">
                {col.links.map((link) => (
                  <li key={link.label}>
                    <a
                      href={link.href}
                      target={link.href.startsWith("http") ? "_blank" : undefined}
                      rel={link.href.startsWith("http") ? "noreferrer" : undefined}
                      className="text-sm transition-colors hover:text-[#f2f4fb]"
                    >
                      {link.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        <div className="mt-12 flex flex-col justify-between gap-2 border-t border-[#161a2b] pt-6 text-xs text-[#5e6580] sm:flex-row">
          <span>
            © {new Date().getFullYear()} Viglet Turing ES — Apache 2.0 License
          </span>
          <span className="space-x-3">
            <a href={LINKS.viglet} target="_blank" rel="noreferrer" className="hover:text-[#f2f4fb]">
              viglet.org
            </a>
            <a href={LINKS.docs} className="hover:text-[#f2f4fb]">
              docs.viglet.org
            </a>
          </span>
        </div>
      </Container>
    </footer>
  );
}
