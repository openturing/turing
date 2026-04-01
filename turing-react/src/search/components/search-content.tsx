import {
  useTuringAutoComplete,
  useTuringSortOptions,
  useTuringUrlSearch,
} from "@viglet/turing-react-sdk";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { AiChatCard } from "./ai-chat-card";
import { JsonSheet } from "./json-sheet";
import { SearchEmpty } from "./search-empty";
import { SearchFooter } from "./search-footer";
import { SearchHeader } from "./search-header";
import { SearchResultsPanel } from "./search-results-panel";
import { SearchError, SearchLoading } from "./search-states";

interface SearchContentProps {
  siteName: string;
}

export function SearchContent({ siteName }: Readonly<SearchContentProps>) {
  const [searchParams, setSearchParams] = useSearchParams();
  const [jsonSheetDoc, setJsonSheetDoc] = useState<Record<string, unknown> | null>(null);

  const turing = useTuringUrlSearch(searchParams, setSearchParams);
  const { suggestions, fetch: fetchSuggestions, clear: clearSuggestions } = useTuringAutoComplete(300);
  const { sortOptions } = useTuringSortOptions();

  if (turing.status === "loading" && !turing.data) return <SearchLoading />;
  if ((turing.status === "error" || !turing.data) && turing.status !== "loading") {
    return <SearchError siteName={siteName} error={turing.error} onShowAll={turing.showAll} />;
  }
  if (!turing.data) return null;

  const { data, chat, documents, params } = turing;

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col">
      <SearchHeader
        siteName={siteName}
        turing={turing}
        locale={params._setlocale || "en_US"}
        locales={data.widget.locales}
        suggestions={suggestions}
        onInputChange={(v) => { turing.setInputValue(v); fetchSuggestions(v, { _setlocale: params._setlocale }); }}
        onSuggestionPick={(term) => { turing.setInputValue(term); clearSuggestions(); }}
        onSuggestionsClose={clearSuggestions}
      />

      <main className="flex-1 container mx-auto px-4 lg:px-8 py-6">
        <AiChatCard chat={chat} />

        {documents.length === 0 ? (
          <SearchEmpty
            query={params.q}
            spellCheck={data.widget.spellCheck}
            chat={chat}
            onNavigate={turing.navigate}
            onShowAll={turing.showAll}
          />
        ) : (
          <SearchResultsPanel
            data={data}
            documents={documents}
            sortOptions={sortOptions}
            currentSort={params.sort || "relevance"}
            onNavigate={turing.navigate}
            onSortChange={turing.setSort}
            onViewJson={setJsonSheetDoc}
          />
        )}
      </main>

      <JsonSheet data={jsonSheetDoc} onClose={() => setJsonSheetDoc(null)} />
      <SearchFooter />
    </div>
  );
}
