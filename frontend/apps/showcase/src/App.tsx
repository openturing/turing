import { Route, Routes } from "react-router-dom";
import { ThemeProvider } from "./components/theme-provider";
import StorefrontPage from "./pages/storefront";
import ProductPage from "./pages/product";

function App() {
  return (
    <ThemeProvider defaultTheme="light" storageKey="atlas-store-theme">
      <Routes>
        <Route path="/" element={<StorefrontPage />} />
        <Route path="/product" element={<ProductPage />} />
      </Routes>
    </ThemeProvider>
  );
}

export default App;
