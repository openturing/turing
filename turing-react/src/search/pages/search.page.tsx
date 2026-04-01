import { TuringProvider } from "@viglet/turing-react-sdk";
import { useParams } from "react-router-dom";
import { SearchContent } from "../components/search-content";

export default function SearchPage() {
  const { siteName } = useParams<{ siteName: string }>();
  const site = siteName || "Sample";

  return (
    <TuringProvider config={{ site, locale: "en_US", sort: "relevance" }}>
      <SearchContent siteName={site} />
    </TuringProvider>
  );
}
