/**
 * T1027 — the cart hook, on its own.
 *
 * It reads the context and throws without a provider. The provider owns the
 * cart state every page in this app renders from, so a module exporting both
 * loses that state on every edit to either.
 */
import { useContext } from "react";
import { CartContext, type CartState } from "./cart-context";

export function useCart(): CartState {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error("useCart must be used within a CartProvider");
  return ctx;
}
