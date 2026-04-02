import { Route, Routes } from "react-router-dom";
import { ThemeProvider } from "./components/theme-provider";
import SearchPage from "./pages/search";
import DetailPage from "./pages/detail";

function App() {
  return (
    <ThemeProvider defaultTheme="dark" storageKey="space-missions-theme">
      <Routes>
        <Route path="/" element={<SearchPage />} />
        <Route path="/detail" element={<DetailPage />} />
      </Routes>
    </ThemeProvider>
  );
}

export default App;
