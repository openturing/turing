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
        inputValue={turing.inputValue}
        locale={params._setlocale || "en_US"}
        locales={data.widget.locales}
        suggestions={suggestions}
        onInputChange={(v) => { turing.setInputValue(v); fetchSuggestions(v); }}
        onSearch={turing.submitSearch}
        onShowAll={turing.showAll}
        onLocaleChange={turing.setLocale}
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
            widget={data.widget}
            documents={documents}
            pagination={data.pagination}
            spellCheck={data.widget.spellCheck}
            sortOptions={sortOptions}
            currentSort={params.sort || "relevance"}
            count={data.queryContext.count}
            pageStart={data.queryContext.pageStart}
            pageEnd={data.queryContext.pageEnd}
            pageCount={data.queryContext.pageCount}
            currentPage={data.queryContext.page}
            isFacetItemTypeOr={data.queryContext.facetItemType?.toUpperCase() === "OR"}
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
