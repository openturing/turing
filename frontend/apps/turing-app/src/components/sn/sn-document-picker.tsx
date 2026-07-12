import { useSnSites } from "@/api/queries/sn-site.queries";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { sanitizeHighlight } from "@/lib/sanitize-html";
import { fetchSearch, type TurDocument, type TurSearchResponse } from "@viglet/turing-react-sdk";
import { IconLoader2, IconSearch } from "@tabler/icons-react";
import { isAxiosError } from "axios";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

/** Strip search-highlight (`<mark>`) and any other markup for stored plain text. */
const stripHtml = (value: string): string => value.replace(/<[^>]*>/g, "");

/** The search API returns English nav labels; map each pagination type to an i18n key. */
const PAGINATION_KEY: Record<string, string> = {
  FIRST: "forms.common.first",
  PREVIOUS: "forms.common.previous",
  NEXT: "forms.common.next",
  LAST: "forms.common.last",
};

export interface PickedSnDocument {
  title: string;
  type: string;
  url: string;
  referenceId: string;
  siteName: string;
  language: string;
}

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onPick: (doc: PickedSnDocument) => void;
}

const EMPTY_RESULT = { results: { document: [] } } as unknown as TurSearchResponse;

/**
 * Reusable indexed-document picker — the same search-and-select mechanism the SN
 * Spotlight editor uses ("Add Documents"), but self-contained and with its own
 * **site** + **language** comboboxes so it can be dropped anywhere that isn't
 * already scoped to a single SN site (e.g. the Persona Match content picker).
 *
 * Searches the public SN search API (`fetchSearch`) scoped by the chosen site +
 * locale; a 404 (site not indexed in that locale, or no matches) renders as an
 * empty result instead of an error.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function SnDocumentPicker({ open, onOpenChange, onPick }: Readonly<Props>) {
  const { t } = useTranslation();
  const { data: sites } = useSnSites();
  const [siteId, setSiteId] = useState("");
  const [language, setLanguage] = useState("");
  const [query, setQuery] = useState("");
  const [result, setResult] = useState<TurSearchResponse | null>(null);
  const [searching, setSearching] = useState(false);

  const site = useMemo(() => sites?.find((s) => s.id === siteId), [sites, siteId]);
  const locales = useMemo(() => {
    const langs = (site?.turSNSiteLocales ?? []).map((l) => l.language).filter(Boolean);
    return Array.from(new Set(langs));
  }, [site]);

  // Default the site to the first available.
  useEffect(() => {
    if (!siteId && sites && sites.length > 0) setSiteId(sites[0].id);
  }, [sites, siteId]);

  // Keep the language valid for the current site; reset stale results.
  useEffect(() => {
    if (locales.length > 0 && !locales.includes(language)) {
      setLanguage(locales[0]);
    }
  }, [locales, language]);

  useEffect(() => {
    setResult(null);
  }, [siteId]);

  async function search(page: number) {
    const siteName = site?.name;
    if (!siteName || !language || !query.trim()) return;
    setSearching(true);
    try {
      const res = await fetchSearch(siteName, {
        q: query.trim(),
        p: page.toString(),
        _setlocale: language,
        sort: "title:desc",
      });
      setResult(res);
    } catch (error) {
      // 404 = site not indexed in this locale / no matches → empty, not an error.
      if (isAxiosError(error) && error.response?.status === 404) {
        setResult(EMPTY_RESULT);
      } else {
        console.error("Document search error", error);
        setResult(EMPTY_RESULT);
      }
    } finally {
      setSearching(false);
    }
  }

  function pick(doc: TurDocument) {
    if (!site) return;
    onPick({
      title: stripHtml(doc.fields.title || "") || doc.fields.id || "",
      type: doc.fields.type || "",
      url: doc.fields.url || "",
      referenceId: doc.fields.id || "",
      siteName: site.name,
      language,
    });
    onOpenChange(false);
    setQuery("");
    setResult(null);
  }

  const documents = result?.results?.document ?? [];

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next);
        if (!next) {
          setQuery("");
          setResult(null);
        }
      }}
    >
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t("forms.snDocumentPicker.title")}</DialogTitle>
          <DialogDescription>{t("forms.snDocumentPicker.description")}</DialogDescription>
        </DialogHeader>

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">{t("forms.snDocumentPicker.site")}</span>
            <Select value={siteId} onValueChange={setSiteId}>
              <SelectTrigger>
                <SelectValue placeholder={t("forms.snDocumentPicker.selectSite")} />
              </SelectTrigger>
              <SelectContent>
                {(sites ?? []).map((s) => (
                  <SelectItem key={s.id} value={s.id}>
                    {s.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">{t("forms.snDocumentPicker.language")}</span>
            <Select value={language} onValueChange={setLanguage} disabled={locales.length === 0}>
              <SelectTrigger>
                <SelectValue placeholder={t("forms.snDocumentPicker.selectLanguage")} />
              </SelectTrigger>
              <SelectContent>
                {locales.map((l) => (
                  <SelectItem key={l} value={l}>
                    {l}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
        </div>

        <div className="flex items-center gap-2">
          <Input
            placeholder={t("forms.snDocumentPicker.searchPlaceholder")}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                search(1);
              }
            }}
            className="grow"
          />
          <Button type="button" variant="outline" size="icon" onClick={() => search(1)} disabled={searching}>
            {searching ? <IconLoader2 className="size-4 animate-spin" /> : <IconSearch className="size-4" />}
          </Button>
        </div>

        {documents.length > 0 && (
          <div className="max-h-80 overflow-y-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("forms.snDocumentPicker.columnTitle")}</TableHead>
                  <TableHead>{t("forms.snDocumentPicker.columnType")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {documents.map((doc, index) => (
                  <TableRow
                    key={doc.fields.id || `doc-${index}`}
                    className="cursor-pointer"
                    onClick={() => pick(doc)}
                  >
                    <TableCell
                      className="[&_mark]:rounded [&_mark]:bg-yellow-200 [&_mark]:px-0.5 [&_mark]:text-black"
                      dangerouslySetInnerHTML={{ __html: sanitizeHighlight(doc.fields.title) }}
                    />
                    <TableCell>{doc.fields.type}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}

        {result && documents.length === 0 && !searching && (
          <p className="py-4 text-center text-sm text-muted-foreground">
            {t("forms.snDocumentPicker.noResults")}
          </p>
        )}

        {result?.pagination && result.pagination.length > 0 && (
          <div className="flex flex-wrap items-center gap-1 pt-1">
            {result.pagination.map((page) => (
              <Button
                key={`${page.type}-${page.page}`}
                type="button"
                variant={page.type === "CURRENT" ? "default" : "outline"}
                size="sm"
                onClick={() => search(page.page)}
              >
                {PAGINATION_KEY[page.type] ? t(PAGINATION_KEY[page.type]) : page.text}
              </Button>
            ))}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
