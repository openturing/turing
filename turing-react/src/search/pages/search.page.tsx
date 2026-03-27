import { BadgeColorful } from "@/components/badge-colorful";
import { JsonViewer } from "@/components/json-viewer";
import { TurLogo } from "@/components/logo/tur-logo";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
} from "@/components/ui/pagination";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { IconBraces } from "@tabler/icons-react";
import { Search } from "lucide-react";
import moment from "moment";
import { useEffect, useState } from "react";
import { NavLink, useNavigate, useParams, useSearchParams } from "react-router-dom";
import type { TurSNChat } from "../models/sn-chat.model";
import type { TurSNSearch } from "../models/sn-search.model";
import { TurSNSearchService } from "../services/sn-search.service";

export default function SearchPage() {
  const { siteName } = useParams<{ siteName: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [snSearch, setSnSearch] = useState<TurSNSearch | null>(null);
  const [llmChat, setLlmChat] = useState<TurSNChat | null>(null);
  const [turQuery, setTurQuery] = useState(searchParams.get("q") || "");
  const [turLocale, setTurLocale] = useState(searchParams.get("_setlocale") || "en_US");
  const [turSort, setTurSort] = useState(searchParams.get("sort") || "relevance");
  const [autoComplete, setAutoComplete] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [jsonSheetDoc, setJsonSheetDoc] = useState<Record<string, unknown> | null>(null);
  const turSiteName = siteName || "Sample"; // Get from URL params or use default
  const navigate = useNavigate();
  const sortOptions: Record<string, string> = {
    relevance: "Relevance",
    newest: "Newest",
    oldest: "Oldest",
  };

  useEffect(() => {
    setTurLocale(searchParams.get("_setlocale") || "en_US");
    setTurSort(searchParams.get("sort") || "relevance");
    performSearch();
  }, [searchParams]);

  const performSearch = async () => {
    setLoading(true);
    setError(null);
    try {
      const q = searchParams.get("q") || "*";
      const p = searchParams.get("p") || "1";
      const _setlocale = searchParams.get("_setlocale") || "pt";
      const sort = searchParams.get("sort") || "relevance";
      const fq = [...searchParams.getAll("fq"), ...searchParams.getAll("fq[]")];
      const tr = [...searchParams.getAll("tr"), ...searchParams.getAll("tr[]")];
      const nfpr = searchParams.get("nfpr") || "";

      const [searchResult, chatResult] = await Promise.all([

        TurSNSearchService.query(turSiteName, {
          q,
          p,
          _setlocale,
          sort,
          fq,
          tr,
          nfpr,
        }),
        q === "*" ? Promise.resolve(null) : TurSNSearchService.chat(turSiteName, { q, _setlocale }),
      ]);

      setSnSearch(searchResult);
      setLlmChat(chatResult);
    } catch (error) {
      console.error("Search error:", error);
      setError("Search failed");
      setSnSearch(null);
    } finally {
      setLoading(false);
    }
  };

  const searchIt = () => {
    const params = new URLSearchParams(searchParams);
    params.set("q", turQuery || "*");
    params.set("p", "1");
    setSearchParams(normalizeSearchString(params));
  };

  const retrieveAutoComplete = async () => {
    if (turQuery && turQuery.length > 2) {
      try {
        const results = await TurSNSearchService.autoComplete(
          turSiteName, {
          q: turQuery,
          p: "1",
          _setlocale: turLocale,
          sort: turSort,
          fq: [],
          tr: [],
          nfpr: ""
        });
        setAutoComplete(results);
      } catch (error) {
        console.error("Autocomplete error:", error);
      }
    } else {
      setAutoComplete([]);
    }
  };

  const turRedirect = (href: string) => {
    console.log("Redirecting to:", href);
    const result: Record<string, string[]> = {};
    const queryString = href.split('?')[1];

    if (queryString) {
      new URLSearchParams(queryString).forEach((value, key) => {
        if (Object.prototype.hasOwnProperty.call(result, key)) {
          result[key].unshift(value);
        } else {
          result[key] = [value];
        }
      });
    }

    const searchParams = new URLSearchParams();
    Object.entries(result).forEach(([key, values]) => {
      values.forEach(val => searchParams.append(key, val));
    });

    const normalizedSearch = normalizeSearchString(searchParams);
    navigate({
      pathname: location.pathname,
      search: `?${normalizedSearch}`
    });

    // performSearch will be triggered by useEffect on searchParams change
  };

  const showAll = () => {
    setTurQuery("*");
    searchIt();
  };

  const changeLocale = (locale: string) => {
    const params = new URLSearchParams(searchParams);
    params.set("_setlocale", locale);
    setTurLocale(locale);
    setSearchParams(normalizeSearchString(params));
  };

  const changeOrderBy = (sort: string) => {
    const params = new URLSearchParams(searchParams);
    params.set("sort", sort);
    setTurSort(sort);
    setSearchParams(normalizeSearchString(params));
  };

  const camelize = (text: string) => {
    return text
      .split("_")
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(" ");
  };

  const normalizeSearchString = (params: URLSearchParams) =>
    params.toString().replaceAll("%5B%5D", "[]");

  const isInternalHref = (href: string) => href.startsWith("/") && !href.startsWith("//");
  const isFacetItemTypeOr = snSearch?.queryContext.facetItemType?.toUpperCase() === "OR";

  if (loading) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="text-center">
          <span className="text-lg">Loading</span>
          <span className="animate-pulse">...</span>
        </div>
      </div>
    );
  }

  if (error || !snSearch) {
    return (
      <div className="min-h-screen bg-background text-foreground">
        <header className="border-b border-border bg-card">
          <div className="container mx-auto px-4 py-4">
            <div className="flex items-center gap-4">
              <TurLogo size={24} />
              <span>{turSiteName}</span>
            </div>
          </div>
        </header>
        <main className="container mx-auto px-4 py-6">
          <div className="text-center py-12">
            <h3 className="text-xl font-semibold mb-2">No content to display</h3>
            <p className="text-muted-foreground mb-4">{error ? "There was an error performing the search." : "Try adjusting your query or locale."}</p>
            <Button size="lg" onClick={showAll}>
              Show all the available content
            </Button>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* Header */}
      <header className="border-b border-border bg-card">
        <div className="container mx-auto px-4 py-4">
          <div className="flex items-center gap-4">
            <Button
              onClick={showAll}
              variant="ghost"
              className="flex items-center gap-2 text-lg font-semibold hover:text-primary"
            >
              <TurLogo size={32} />
              <span>{turSiteName}</span>
            </Button>

            <div className="flex-1 relative">
              <Input
                value={turQuery}
                onChange={(e) => {
                  setTurQuery(e.target.value);
                  retrieveAutoComplete();
                }}
                onKeyDown={(e) => e.key === "Enter" && searchIt()}
                onBlur={() => {
                  globalThis.setTimeout(() => setAutoComplete([]), 100);
                }}
                type="search"
                className="pr-10"
                placeholder="Search..."
                autoComplete="off"
              />
              <Search className="absolute right-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
              {autoComplete.length > 0 && (
                <div className="absolute z-50 mt-2 w-full rounded-md border border-border bg-popover text-popover-foreground shadow-md">
                  {autoComplete.map((term) => (
                    <Button
                      key={term}
                      type="button"
                      variant="ghost"
                      className="w-full justify-start rounded-none"
                      onMouseDown={(event) => event.preventDefault()}
                      onClick={() => {
                        setTurQuery(term);
                        setAutoComplete([]);
                      }}
                    >
                      {term}
                    </Button>
                  ))}
                </div>
              )}
            </div>

            {/* Locale Selector */}
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">Language</span>
              <Select value={turLocale} onValueChange={changeLocale}>
                <SelectTrigger className="w-40">
                  <SelectValue placeholder="Select" />
                </SelectTrigger>
                <SelectContent>
                  {snSearch.widget.locales.map((locale) => (
                    <SelectItem key={locale.locale} value={locale.locale}>
                      {locale.locale}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="container mx-auto px-4 py-6 min-h-150">
        {/* Chat/AI Response */}
        {llmChat?.text && (
          <div className="mb-6 p-6 bg-card border border-border rounded-md">
            <h3 className="text-xl font-semibold mb-2">Assistant</h3>
            <p className="whitespace-pre-wrap">{llmChat.text}</p>
          </div>
        )}

        {/* No Results */}
        {snSearch.results.document.length === 0 && (
          <div className="text-center py-12">
            <Search className="w-12 h-12 mx-auto mb-4 text-muted-foreground" />
            <h3 className="text-xl font-semibold mb-2">
              We couldn't find any results matching '{turQuery}'.
            </h3>
            {snSearch.widget.spellCheck.correctedText && (
              <h3 className="text-lg mb-4">
                Did you mean{" "}
                <Button
                  variant="link"
                  className="p-0 h-auto text-primary underline font-semibold"
                  onClick={() => turRedirect(snSearch.widget.spellCheck.corrected.link)}
                >
                  {snSearch.widget.spellCheck.corrected.text}
                </Button>
                ?
              </h3>
            )}
            {!snSearch.widget.spellCheck.correctedText && !llmChat?.text && (
              <div>
                <p className="mb-4">
                  You can try to see all the available content, maybe you have a new idea. :-)
                </p>
                <Button
                  size="lg"
                  onClick={showAll}
                >
                  Show all the available content
                </Button>
              </div>
            )}
          </div>
        )}

        {/* Results */}
        {snSearch.results.document.length > 0 && (
          <div className="flex gap-6">
            {/* Facets Sidebar */}
            <aside className="w-64 shrink-0">
              <Accordion type="multiple" className="space-y-4">
                {/* Applied Filters */}
                {snSearch.widget.facetToRemove?.facets && (
                  <AccordionItem value="applied-filters" className="border border-primary rounded-md">
                    <AccordionTrigger className="px-4 py-3 text-base">
                      Applied Filters
                    </AccordionTrigger>
                    <AccordionContent className="pt-0">
                      <div className="px-4 pb-2 flex justify-end border-b border-primary">
                        <Button
                          variant="link"
                          className="p-0 h-auto text-sm text-primary rounded-none"
                          onClick={() => turRedirect(snSearch.widget.cleanUpFacets)}
                        >
                          Clean up all
                        </Button>
                      </div>
                      <div>
                        {snSearch.widget.facetToRemove.facets.map((facet, index) => (
                          <Button
                            key={`${facet.label || facet.link}-${facet.link}`}
                            variant="ghost"
                            className={`w-full px-4 py-2 text-left hover:bg-accent text-sm justify-start rounded-none ${index < snSearch.widget.facetToRemove.facets.length - 1
                              ? "border-b border-border"
                              : ""
                              }`}
                            onClick={() => turRedirect(facet.link)}
                          >
                            <span>{facet.label}</span>
                            <span className="float-right">(Remove)</span>
                          </Button>
                        ))}
                      </div>
                    </AccordionContent>
                  </AccordionItem>
                )}

                {/* Facet Groups */}
                {snSearch.widget.facet.map((facets) => (
                  <AccordionItem
                    key={`${facets.label.text}-${facets.cleanUpLink}`}
                    value={`facet-${facets.label.text}-${facets.cleanUpLink}`}
                    className="border border-border rounded-md"
                  >
                    <AccordionTrigger className="px-4 py-3 text-base">
                      {facets.label.text}
                    </AccordionTrigger>
                    <AccordionContent className="py-0">
                      <div className="px-4 pb-2 flex justify-end border-b border-border">
                        <Button
                          variant="ghost"
                          className="p-0 h-auto text-sm text-primary"
                          onClick={() => turRedirect(facets.cleanUpLink)}
                        >
                          Clean up
                        </Button>
                      </div>
                      <div>
                        {facets.facets.map((facet, index) => (
                          <Button
                            key={`${facet.label || facet.link}-${facet.link}`}
                            variant="ghost"
                            className={`w-full px-4 py-2 text-left hover:bg-accent text-sm flex items-center rounded-none ${index < facets.facets.length - 1 ? "border-b border-border" : ""
                              }`}
                            onClick={() => turRedirect(facet.link)}
                          >
                            <span className="w-full flex items-center justify-between">
                              <span>
                                {facet.label}{" "}
                                <span className="inline-block px-2 py-0.5 bg-muted text-muted-foreground rounded-full text-xs">
                                  {facet.count}
                                </span>
                              </span>
                              {isFacetItemTypeOr && (
                                <Checkbox
                                  checked={Boolean(facet.selected)}
                                  aria-label={facet.label}
                                  className="pointer-events-none"
                                />
                              )}
                            </span>
                          </Button>
                        ))}
                      </div>
                    </AccordionContent>
                  </AccordionItem>
                ))}
              </Accordion>
            </aside>

            {/* Results Column */}
            <div className="flex-1">
              <div className="flex justify-between items-center mb-4">
                <h3 className="text-lg font-semibold">
                  Showing {snSearch.queryContext.pageStart} - {snSearch.queryContext.pageEnd} of{" "}
                  {snSearch.queryContext.count} results
                </h3>

                {/* Sort Selector */}
                <div className="flex items-center gap-2">
                  <span className="text-sm text-muted-foreground">Order by</span>
                  <Select value={turSort} onValueChange={changeOrderBy}>
                    <SelectTrigger className="w-40">
                      <SelectValue placeholder="Select" />
                    </SelectTrigger>
                    <SelectContent>
                      {Object.entries(sortOptions).map(([key, value]) => (
                        <SelectItem key={key} value={key}>
                          {value}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              {/* Spell Check Messages */}
              {snSearch.widget.spellCheck.usingCorrectedText &&
                snSearch.widget.spellCheck.correctedText && (
                  <div className="mb-4">
                    <h4 className="mb-1">
                      Showing results for{" "}
                      <Button
                        variant="link"
                        className="p-0 h-auto text-primary underline font-semibold italic"
                        onClick={() => turRedirect(snSearch.widget.spellCheck.corrected.link)}
                      >
                        {snSearch.widget.spellCheck.corrected.text}
                      </Button>
                      .
                    </h4>
                    <p>
                      Instead, search for{" "}
                      <Button
                        variant="link"
                        className="p-0 h-auto text-primary underline font-semibold"
                        onClick={() => turRedirect(snSearch.widget.spellCheck.original.link)}
                      >
                        {snSearch.widget.spellCheck.original.text}
                      </Button>
                    </p>
                  </div>
                )}

              {!snSearch.widget.spellCheck.usingCorrectedText &&
                snSearch.widget.spellCheck.correctedText && (
                  <div className="mb-4">
                    <h4 className="mb-1">
                      Did you mean{" "}
                      <Button
                        variant="link"
                        className="p-0 h-auto text-primary underline font-semibold italic"
                        onClick={() => turRedirect(snSearch.widget.spellCheck.corrected.link)}
                      >
                        {snSearch.widget.spellCheck.corrected.text}
                      </Button>
                      ?
                    </h4>
                  </div>
                )}

              {/* Documents */}
              <div className="space-y-4">
                {snSearch.results.document.map((document) => {
                  const url = document.fields[snSearch.queryContext.defaultFields.url];
                  const title = document.fields[snSearch.queryContext.defaultFields.title];
                  const description =
                    document.fields[snSearch.queryContext.defaultFields.description];
                  const date = document.fields[snSearch.queryContext.defaultFields.date];

                  if (!url) return null;

                  return (
                    <div
                      key={url}
                      className="border-t border-border pt-4 flex gap-4"
                    >
                      <div className="flex-1">
                        <h4 className="text-lg mb-1">
                          {isInternalHref(url) ? (
                            <NavLink
                              to={url}
                              className="text-primary hover:underline"
                              dangerouslySetInnerHTML={{ __html: title || url }}
                            />
                          ) : (
                            <a
                              href={url}
                              className="text-primary hover:underline"
                              target="_blank"
                              rel="noopener noreferrer"
                              dangerouslySetInnerHTML={{ __html: title || url }}
                            />
                          )}
                        </h4>
                        {description && (
                          <p
                            className="text-sm text-muted-foreground mb-2 line-clamp-2"
                            dangerouslySetInnerHTML={{ __html: description }}
                          />
                        )}
                        {/* Metadata Badges */}
                        <div className="flex flex-wrap gap-2">
                          {Array.isArray(document.metadata) && document.metadata.map((metadata) => {
                            if (!metadata?.text || !metadata?.href) return null;
                            return (
                              <BadgeColorful
                                key={`${metadata.text}-${metadata.href}`}
                                text={metadata.text}
                                href={metadata.href}
                                onClick={turRedirect}
                              />
                            );
                          })}
                        </div>
                        {/* Document Date */}
                        {date && (
                          <div className="text-xs text-muted-foreground mt-2">
                            Updated {moment(date).format("LL")}
                          </div>
                        )}
                      </div>
                      <div className="shrink-0">
                        <Button
                          variant="ghost"
                          size="icon"
                          title="View JSON"
                          onClick={() => setJsonSheetDoc(document as unknown as Record<string, unknown>)}
                        >
                          <IconBraces className="w-4 h-4" />
                        </Button>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Pagination */}
              <Pagination className="mt-6">
                <PaginationContent>
                  {snSearch.pagination.map((page) => {
                    const keyBase = `${page.type}-${page.href || ""}-${page.text}`;
                    const isEllipsis = page.type === "ELLIPSIS" || page.text === "...";
                    const isCurrent = page.type === "CURRENT";

                    if (isEllipsis) {
                      return (
                        <PaginationItem key={`ellipsis-${keyBase}`}>
                          <PaginationEllipsis />
                        </PaginationItem>
                      );
                    }

                    return (
                      <PaginationItem key={keyBase}>
                        <PaginationLink
                          href={page.href || "#"}
                          isActive={isCurrent}
                          size="default"
                          onClick={(event) => {
                            event.preventDefault();
                            if (!isCurrent && page.href) {
                              turRedirect(page.href);
                            }
                          }}
                        >
                          {camelize(page.text)}
                        </PaginationLink>
                      </PaginationItem>
                    );
                  })}
                </PaginationContent>
              </Pagination>
            </div>
          </div>
        )}
      </main>

      {/* JSON Viewer Sheet */}
      <Sheet open={jsonSheetDoc !== null} onOpenChange={(open) => { if (!open) setJsonSheetDoc(null); }}>
        <SheetContent side="right" className="w-full sm:max-w-2xl flex flex-col">
          <SheetHeader>
            <SheetTitle className="flex items-center gap-2">
              <IconBraces className="w-5 h-5" />
              Document JSON
            </SheetTitle>
          </SheetHeader>
          <div className="flex-1 overflow-auto mt-4">
            <JsonViewer data={jsonSheetDoc} className="h-full" />
          </div>
        </SheetContent>
      </Sheet>

      {/* Footer */}
      <footer className="border-t border-border mt-12">
        <div className="container mx-auto px-4 py-6">
          <div className="flex flex-wrap justify-between items-center gap-4 text-sm text-muted-foreground">
            <div className="flex gap-4">
              <span>© {new Date().getFullYear()} Turing ES.</span>
              <a href="https://github.com/openturing" target="_blank" rel="noopener noreferrer" className="hover:text-foreground">
                Github
              </a>
              <a href="https://linkedin.com/company/viglet.com" target="_blank" rel="noopener noreferrer" className="hover:text-foreground">
                LinkedIn
              </a>
              <NavLink to="/swagger-ui.html" className="hover:text-foreground">
                API
              </NavLink>
              <NavLink to="/console" className="hover:text-foreground">
                Console
              </NavLink>
            </div>
            <div className="flex gap-4">
              <a href="https://viglet.org/#contact" target="_blank" rel="noopener noreferrer" className="hover:text-foreground">
                Contact
              </a>
              <a href="https://viglet.org" target="_blank" rel="noopener noreferrer" className="hover:text-foreground">
                Viglet
              </a>
              <a href="https://docs.viglet.org/turing" target="_blank" rel="noopener noreferrer" className="hover:text-foreground">
                Documentation
              </a>
              <a href="https://viglet.org/turing" target="_blank" rel="noopener noreferrer" className="hover:text-foreground">
                Website
              </a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
