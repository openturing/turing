import { Route, Routes } from "react-router-dom";
import { ThemeProvider } from "./components/theme-provider";
import { CartProvider } from "./contexts/cart";
import StorefrontPage from "./pages/storefront";
import ProductPage from "./pages/product";

function App() {
  return (
    <ThemeProvider defaultTheme="light" storageKey="atlas-store-theme">
      <CartProvider>
        <Routes>
          <Route path="/" element={<StorefrontPage />} />
          <Route path="/product" element={<ProductPage />} />
        </Routes>
      </CartProvider>
    </ThemeProvider>
  );
}

export default App;
