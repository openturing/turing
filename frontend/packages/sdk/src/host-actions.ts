/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
import type { ClientToolHandler } from "./controllers/chat";

/**
 * T450 / §XXIII.9 — embeddable action widget: default host-action client-tool
 * handlers that let the agent ACT on the page the embedded widget runs in
 * (navigate, fill a form, click, add to cart). Spread the returned
 * {@link HostActions.clientTools} into {@code createChatController({ clientTools })};
 * the names match the backend's built-in action tools (advertised when the agent
 * has {@code actionWidgetEnabled}). Turing becomes an action layer over any
 * website — no browser extension.
 *
 * <p>Zero-dependency and DOM-driven, with sensible safe defaults the host can
 * override:
 * <ul>
 *   <li>{@code navigate} — assigns {@code location.href} (override with {@code onNavigate});
 *       disabled unless {@code allowNavigate} is true.</li>
 *   <li>{@code fill_form} — sets matching inputs' values + dispatches an input event.</li>
 *   <li>{@code click_element} — clicks the first element matching the selector.</li>
 *   <li>{@code add_to_cart} — host-specific, so it requires an {@code onAddToCart} callback.</li>
 * </ul>
 * Add or replace handlers via {@code extra}. Every handler returns a small
 * {@code {success, ...}} result the agent reads; nothing throws into the UI.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export interface HostActionsOptions {
  /** Allow the {@code navigate} tool to change the page location. Default false. */
  readonly allowNavigate?: boolean;
  /** Override navigation (e.g. SPA router push). Receives the requested URL. */
  readonly onNavigate?: (url: string) => void;
  /** Implement add-to-cart for the host store. Without it, {@code add_to_cart} no-ops. */
  readonly onAddToCart?: (args: {
    productId: string;
    quantity: number;
  }) => unknown | Promise<unknown>;
  /** Root to scope DOM queries (defaults to {@code document}). */
  readonly root?: ParentNode;
  /** Extra/overriding handlers merged over the defaults. */
  readonly extra?: Record<string, ClientToolHandler>;
}

export interface HostActions {
  /** Spread into {@code createChatController}'s {@code clientTools}. */
  clientTools: Record<string, ClientToolHandler>;
}

interface FormField {
  selector?: unknown;
  value?: unknown;
}

function hasDom(): boolean {
  return typeof document !== "undefined";
}

function queryInput(root: ParentNode, selector: string): HTMLElement | null {
  // Try as a CSS selector first, then fall back to matching by input name.
  try {
    const bySelector = root.querySelector<HTMLElement>(selector);
    if (bySelector) return bySelector;
  } catch {
    // invalid selector — fall through to name lookup
  }
  return root.querySelector<HTMLElement>(`[name="${CSS.escape(selector)}"]`);
}

function setFieldValue(el: HTMLElement, value: string): boolean {
  if (
    el instanceof HTMLInputElement ||
    el instanceof HTMLTextAreaElement ||
    el instanceof HTMLSelectElement
  ) {
    el.value = value;
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
    return true;
  }
  return false;
}

export function createHostActions(options: HostActionsOptions = {}): HostActions {
  const { allowNavigate = false, onNavigate, onAddToCart, root, extra } = options;
  const scope: ParentNode | null = root ?? (hasDom() ? document : null);

  const navigate: ClientToolHandler = (args) => {
    const url = String((args as { url?: unknown })?.url ?? "").trim();
    if (!url) return { success: false, error: "url is required" };
    if (onNavigate) {
      onNavigate(url);
      return { success: true, url };
    }
    if (!allowNavigate || typeof location === "undefined") {
      return { success: false, error: "navigation is not enabled by the host" };
    }
    location.href = url;
    return { success: true, url };
  };

  const fillForm: ClientToolHandler = (args) => {
    if (!scope) return { success: false, error: "no DOM available" };
    const fields = (args as { fields?: FormField[] })?.fields ?? [];
    let filled = 0;
    const missing: string[] = [];
    for (const field of fields) {
      const selector = typeof field?.selector === "string" ? field.selector : "";
      if (!selector) continue;
      const el = queryInput(scope, selector);
      if (el && setFieldValue(el, String(field?.value ?? ""))) {
        filled++;
      } else {
        missing.push(selector);
      }
    }
    return { success: missing.length === 0, filled, missing };
  };

  const clickElement: ClientToolHandler = (args) => {
    if (!scope) return { success: false, error: "no DOM available" };
    const selector = String((args as { selector?: unknown })?.selector ?? "").trim();
    if (!selector) return { success: false, error: "selector is required" };
    let el: HTMLElement | null = null;
    try {
      el = scope.querySelector<HTMLElement>(selector);
    } catch {
      return { success: false, error: "invalid selector" };
    }
    if (!el) return { success: false, error: `no element matches ${selector}` };
    el.click();
    return { success: true, selector };
  };

  const addToCart: ClientToolHandler = async (args) => {
    const productId = String((args as { productId?: unknown })?.productId ?? "").trim();
    if (!productId) return { success: false, error: "productId is required" };
    if (!onAddToCart) {
      return { success: false, error: "the host has not wired add_to_cart" };
    }
    const rawQty = (args as { quantity?: unknown })?.quantity;
    const quantity = Math.max(1, Math.trunc(Number(rawQty)) || 1);
    const result = await onAddToCart({ productId, quantity });
    return { success: true, productId, quantity, result };
  };

  return {
    clientTools: {
      navigate,
      fill_form: fillForm,
      click_element: clickElement,
      add_to_cart: addToCart,
      ...(extra ?? {}),
    },
  };
}
