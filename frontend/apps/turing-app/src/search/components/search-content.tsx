import {
  useSearchStore,
  useTuringAutoComplete,
  useTuringSortOptions,
} from "@viglet/turing-react-sdk";
import { useState } from "react";
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
  const [jsonSheetDoc, setJsonSheetDoc] = useState<Record<string, unknown> | null>(null);

  const store = useSearchStore();
  const { suggestions, fetch: fetchSuggestions, clear: clearSuggestions } = useTuringAutoComplete(300);
  const { sortOptions } = useTuringSortOptions();

  if (store.status === "loading" && !store.data) return <SearchLoading />;
  if ((store.status === "error" || !store.data) && store.status !== "loading") {
    return <SearchError siteName={siteName} error={store.error} onShowAll={store.showAll} />;
  }
  if (!store.data) return null;

  const { data, chat, documents, params } = store;
  const defaultLocale = data.widget.locales[0]?.locale ?? "";
  const currentLocale = params._setlocale || defaultLocale;

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col">
      <SearchHeader
        siteName={siteName}
        siteIcon={data.widget.icon}
        store={store}
        locale={currentLocale}
        locales={data.widget.locales}
        suggestions={suggestions}
        onInputChange={(v) => { store.setInputValue(v); fetchSuggestions(v, { _setlocale: params._setlocale }); }}
        onSuggestionPick={(term) => { store.setInputValue(term); clearSuggestions(); }}
        onSuggestionsClose={clearSuggestions}
      />

      <main className="flex-1 container mx-auto px-4 lg:px-8 py-6">
        <AiChatCard chat={chat} />

        {documents.length === 0 ? (
          <SearchEmpty
            query={params.q}
            spellCheck={data.widget.spellCheck}
            chat={chat}
            onNavigate={store.navigate}
            onShowAll={store.showAll}
          />
        ) : (
          <SearchResultsPanel
            data={data}
            documents={documents}
            sortOptions={sortOptions}
            currentSort={params.sort || "relevance"}
            onSortChange={store.setSort}
            onViewJson={setJsonSheetDoc}
          />
        )}
      </main>

      <JsonSheet data={jsonSheetDoc} onClose={() => setJsonSheetDoc(null)} />
      <SearchFooter />
    </div>
  );
}
