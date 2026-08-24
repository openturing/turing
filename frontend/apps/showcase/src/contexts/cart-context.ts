/**
 * T1027 — the cart context and the shape it carries, on their own.
 *
 * The provider renders and the hook reads. A context object is neither, and a
 * module exporting one beside a component reloads whole — which for the cart
 * means every page loses its basket on an edit.
 */
import { createContext } from "react";

export interface CartItem {
  sku: string;
  title: string;
  price: number;
  currency: string;
  image?: string;
  qty: number;
}

export interface CartState {
  items: CartItem[];
  count: number;
  total: number;
  add: (item: Omit<CartItem, "qty">, qty?: number) => void;
  remove: (sku: string) => void;
  clear: () => void;
}

export const CartContext = createContext<CartState | null>(null);
