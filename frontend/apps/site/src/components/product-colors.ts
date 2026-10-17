/**
 * T1027: the per-product brand colours, on their own.
 *
 * A record of hex strings that four modules read. It rendered nothing, and a
 * module exporting both it and a component reloads whole instead of
 * preserving state.
 */
export const PRODUCT_COLORS: Record<string, string> = {
  dumont: "#0F766E", // teal
  shio: "#7C3AED", // violet
  turing: "#4169E1", // royalblue
};
