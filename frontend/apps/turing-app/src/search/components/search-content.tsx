import {
  fetchChatEnabled,
  useSearchStore,
  useTuringAutoComplete,
  useTuringSortOptions,
} from "@viglet/turing-react-sdk";
import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { AiChatCard } from "./ai-chat-card";
import { AiModeChat, type AiModeChatHandle } from "./ai-mode-chat";
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
  const [aiModeAvailable, setAiModeAvailable] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const aiModeActive = searchParams.get("_aimode") === "true";

  const store = useSearchStore();
  const { suggestions, fetch: fetchSuggestions, clear: clearSuggestions } = useTuringAutoComplete(300);
  const { sortOptions } = useTuringSortOptions();
  const aiModeChatRef = useRef<AiModeChatHandle>(null);

  const handleAiModeSubmit = useCallback(() => {
    const query = store.inputValue?.trim();
    if (!query || query === "*") return;
    aiModeChatRef.current?.ask(query);
    store.setInputValue("");
    clearSuggestions();
  }, [store, clearSuggestions]);

  useEffect(() => {
    let cancelled = false;
    fetchChatEnabled(siteName)
      .then((result) => {
        if (!cancelled) setAiModeAvailable(!!result?.enabled);
      })
      .catch(() => {
        if (!cancelled) setAiModeAvailable(false);
      });
    return () => {
      cancelled = true;
    };
  }, [siteName]);

  const toggleAiMode = () => {
    const next = new URLSearchParams(searchParams);
    if (aiModeActive) {
      next.delete("_aimode");
    } else {
      next.set("_aimode", "true");
    }
    setSearchParams(next, { replace: false });
  };

  if (store.status === "loading" && !store.data) return <SearchLoading />;
  if ((store.status === "error" || !store.data) && store.status !== "loading") {
    return <SearchError siteName={siteName} error={store.error} onShowAll={store.showAll} />;
  }
  if (!store.data) return null;

  const { data, chat, documents, params } = store;
  const defaultLocale = data.widget.locales[0]?.locale ?? "";
  const currentLocale = params._setlocale || defaultLocale;
  const showAiMode = aiModeActive && aiModeAvailable;

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
        aiModeAvailable={aiModeAvailable}
        aiModeActive={aiModeActive}
        onToggleAiMode={toggleAiMode}
        onSubmit={showAiMode ? handleAiModeSubmit : undefined}
      />

      <main className="flex-1 container mx-auto px-4 lg:px-8 py-6">
        {showAiMode ? (
          <AiModeChat
            ref={aiModeChatRef}
            siteName={siteName}
            locale={currentLocale}
            initialQuery={
              store.inputValue && store.inputValue !== "*"
                ? store.inputValue
                : params.q && params.q !== "*" ? params.q : undefined
            }
            onInitialQueryConsumed={() => store.setInputValue("")}
          />
        ) : (
          <>
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
          </>
        )}
      </main>

      <JsonSheet data={jsonSheetDoc} onClose={() => setJsonSheetDoc(null)} />
      <SearchFooter />
    </div>
  );
}
