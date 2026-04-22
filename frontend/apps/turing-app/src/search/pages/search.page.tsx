import { TuringProvider } from "@viglet/turing-react-sdk";
import { useParams, useSearchParams } from "react-router-dom";
import { SearchContent } from "../components/search-content";

export default function SearchPage() {
  const { siteName } = useParams<{ siteName: string }>();
  const site = siteName || "Sample";
  const [searchParams, setSearchParams] = useSearchParams();

  return (
    <TuringProvider
      config={{ site, sort: "relevance" }}
      urlSync={{ searchParams, setSearchParams: (s) => setSearchParams(s) }}
    >
      <SearchContent siteName={site} />
    </TuringProvider>
  );
}
