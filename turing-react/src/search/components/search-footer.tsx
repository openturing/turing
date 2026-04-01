import { TurLogo } from "@/components/logo/tur-logo";
import { NavLink } from "react-router-dom";

const LINKS_LEFT = [
  { href: "https://github.com/openturing", label: "Github", external: true },
  { href: "https://linkedin.com/company/viglet.com", label: "LinkedIn", external: true },
  { href: "/swagger-ui.html", label: "API", external: false },
  { href: "/console", label: "Console", external: false },
];

const LINKS_RIGHT = [
  { href: "https://docs.viglet.org/turing", label: "Docs", external: true },
  { href: "https://viglet.org/#contact", label: "Contact", external: true },
  { href: "https://viglet.org", label: "Viglet", external: true },
];

export function SearchFooter() {
  return (
    <footer className="border-t border-border/50 mt-16">
      <div className="container mx-auto px-4 lg:px-8 py-8">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-6">
          {/* Brand */}
          <div className="flex items-center gap-2 text-muted-foreground">
            <TurLogo size={20} />
            <span className="text-xs">
              &copy; {new Date().getFullYear()} Viglet Turing ES
            </span>
          </div>

          {/* Links */}
          <nav className="flex flex-wrap justify-center gap-x-5 gap-y-2">
            {[...LINKS_LEFT, ...LINKS_RIGHT].map(({ href, label, external }) =>
              external ? (
                <a
                  key={label}
                  href={href}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs text-muted-foreground hover:text-foreground transition-colors"
                >
                  {label}
                </a>
              ) : (
                <NavLink
                  key={label}
                  to={href}
                  className="text-xs text-muted-foreground hover:text-foreground transition-colors"
                >
                  {label}
                </NavLink>
              ),
            )}
          </nav>
        </div>
      </div>
    </footer>
  );
}
