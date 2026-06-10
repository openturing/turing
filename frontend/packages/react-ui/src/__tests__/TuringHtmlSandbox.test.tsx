import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { renderToStaticMarkup } from "react-dom/server";
import { TuringHtmlSandbox } from "../TuringHtmlSandbox";

/**
 * T301 — render tests for the headless contract of `TuringHtmlSandbox`:
 *   1. iframe `sandbox` / `srcDoc` attributes
 *   2. fullscreen open / Esc (+ close button) via the portal
 *   3. `classNames` / `labels` / `icons` passthrough
 *   4. SSR `document` guard (default render is server-safe)
 */

describe("TuringHtmlSandbox — iframe sandbox/srcDoc contract", () => {
  it("renders a sandboxed iframe with allow-scripts but NOT allow-same-origin", () => {
    const { container } = render(<TuringHtmlSandbox code="<b>hi</b>" />);
    const iframe = container.querySelector("iframe");
    expect(iframe).toBeInTheDocument();
    const sandbox = iframe?.getAttribute("sandbox");
    expect(sandbox).toBe("allow-scripts");
    // The sandbox MUST NOT grant same-origin — that's the whole isolation point.
    expect(sandbox).not.toContain("allow-same-origin");
  });

  it("wraps the fragment in a self-contained srcDoc document", () => {
    const { container } = render(<TuringHtmlSandbox code="<h1>Hello</h1>" />);
    const srcDoc = container.querySelector("iframe")?.getAttribute("srcdoc") ?? "";
    expect(srcDoc).toContain("<!DOCTYPE html>");
    expect(srcDoc).toContain("<h1>Hello</h1>");
  });

  it("applies the height prop to the inline iframe", () => {
    const { container } = render(<TuringHtmlSandbox code="x" height={500} />);
    expect(container.querySelector("iframe")?.style.height).toBe("500px");
  });
});

describe("TuringHtmlSandbox — code/preview toggle", () => {
  it("hides the toggle by default", () => {
    render(<TuringHtmlSandbox code="x" />);
    expect(screen.queryByText("Code")).toBeNull();
  });

  it("swaps the iframe for the source when toggled, and back", () => {
    const { container } = render(
      <TuringHtmlSandbox code="<i>src</i>" showCodeToggle />,
    );
    expect(container.querySelector("iframe")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Code"));
    expect(container.querySelector("iframe")).toBeNull();
    expect(container.querySelector("pre code")?.textContent).toBe("<i>src</i>");

    // Target the toggle by role — the header label also reads "Preview" (the
    // default title), so getByText would be ambiguous here.
    fireEvent.click(screen.getByRole("button", { name: "Preview" }));
    expect(container.querySelector("iframe")).toBeInTheDocument();
  });
});

describe("TuringHtmlSandbox — fullscreen open/close", () => {
  it("portals a second iframe on fullscreen and removes it on Esc", () => {
    render(<TuringHtmlSandbox code="<p>fs</p>" />);
    expect(document.querySelectorAll("iframe")).toHaveLength(1);

    fireEvent.click(screen.getByRole("button", { name: "Fullscreen" }));
    expect(document.querySelectorAll("iframe")).toHaveLength(2);

    fireEvent.keyDown(document, { key: "Escape" });
    expect(document.querySelectorAll("iframe")).toHaveLength(1);
  });

  it("closes via the close button", () => {
    render(<TuringHtmlSandbox code="x" />);
    fireEvent.click(screen.getByRole("button", { name: "Fullscreen" }));
    expect(document.querySelectorAll("iframe")).toHaveLength(2);

    fireEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(document.querySelectorAll("iframe")).toHaveLength(1);
  });
});

describe("TuringHtmlSandbox — fullscreen a11y (T304)", () => {
  it("exposes the portal as a labelled modal dialog", () => {
    render(<TuringHtmlSandbox code="x" labels={{ title: "Demo" }} />);
    fireEvent.click(screen.getByRole("button", { name: "Fullscreen" }));

    const dialog = screen.getByRole("dialog", { name: "Demo" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
  });

  it("moves focus into the dialog on open and restores it to the trigger on close", () => {
    render(<TuringHtmlSandbox code="x" />);
    const trigger = screen.getByRole("button", { name: "Fullscreen" });
    trigger.focus();
    expect(document.activeElement).toBe(trigger);

    fireEvent.click(trigger);
    // Focus lands on the first focusable inside the dialog (the close button).
    expect(document.activeElement).toBe(
      screen.getByRole("button", { name: "Close" }),
    );

    fireEvent.keyDown(document, { key: "Escape" });
    // Focus returns to whatever opened the dialog.
    expect(document.activeElement).toBe(trigger);
  });

  it("gives the code/preview toggle an accessible name even with an icon", () => {
    render(
      <TuringHtmlSandbox
        code="x"
        showCodeToggle
        icons={{ code: <span data-testid="code-icon">{"</>"}</span> }}
      />,
    );
    // Icon replaces the text, but the button still has an accessible name.
    expect(screen.getByTestId("code-icon")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Code" }),
    ).toBeInTheDocument();
  });
});

describe("TuringHtmlSandbox — classNames / labels / icons passthrough", () => {
  it("applies per-slot class names (and `className` as the root alias)", () => {
    const { container } = render(
      <TuringHtmlSandbox
        code="x"
        showCodeToggle
        classNames={{
          root: "my-root",
          header: "my-header",
          label: "my-label",
          actions: "my-actions",
          button: "my-button",
          iframe: "my-iframe",
        }}
      />,
    );
    expect(container.querySelector(".my-root")).toBeInTheDocument();
    expect(container.querySelector(".my-header")).toBeInTheDocument();
    expect(container.querySelector(".my-label")).toBeInTheDocument();
    expect(container.querySelector(".my-actions")).toBeInTheDocument();
    expect(container.querySelector("iframe.my-iframe")).toBeInTheDocument();
    // both toolbar buttons (code toggle + fullscreen) carry the button class
    expect(container.querySelectorAll(".my-button")).toHaveLength(2);
  });

  it("treats `className` as a convenience alias for classNames.root", () => {
    const { container } = render(
      <TuringHtmlSandbox code="x" className="alias-root" />,
    );
    expect(container.querySelector(".alias-root")).toBeInTheDocument();
  });

  it("applies fullscreen-slot class names inside the portal", () => {
    render(
      <TuringHtmlSandbox
        code="x"
        classNames={{
          fullscreenRoot: "fs-root",
          fullscreenHeader: "fs-header",
          fullscreenLabel: "fs-label",
          fullscreenButton: "fs-button",
          fullscreenIframe: "fs-iframe",
        }}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Fullscreen" }));
    expect(document.querySelector(".fs-root")).toBeInTheDocument();
    expect(document.querySelector(".fs-header")).toBeInTheDocument();
    expect(document.querySelector(".fs-label")).toBeInTheDocument();
    expect(document.querySelector(".fs-button")).toBeInTheDocument();
    expect(document.querySelector("iframe.fs-iframe")).toBeInTheDocument();
  });

  it("uses localized labels and renders icons in place of label text", () => {
    render(
      <TuringHtmlSandbox
        code="x"
        showCodeToggle
        labels={{
          title: "Pré-visualização",
          fullscreen: "Tela cheia",
          showCode: "Código",
        }}
        icons={{ fullscreen: <span data-testid="fs-icon">⤢</span> }}
      />,
    );
    expect(screen.getByText("Pré-visualização")).toBeInTheDocument();
    // the supplied icon replaces the fullscreen label text...
    expect(screen.getByTestId("fs-icon")).toBeInTheDocument();
    // ...while the accessible name still comes from the localized label
    expect(
      screen.getByRole("button", { name: "Tela cheia" }),
    ).toBeInTheDocument();
    // no icon supplied for the code toggle → it falls back to the localized label
    expect(screen.getByText("Código")).toBeInTheDocument();
  });
});

describe("TuringHtmlSandbox — SSR safety", () => {
  it("renders to static markup without touching the portal branch", () => {
    // The default (non-fullscreen) render never reaches createPortal/document,
    // so it is server-renderable. The click-only fullscreen branch additionally
    // guards `typeof document === "undefined"` and returns null on the server.
    const html = renderToStaticMarkup(<TuringHtmlSandbox code="<b>ssr</b>" />);
    expect(html).toContain("<iframe");
    expect(html).toContain('sandbox="allow-scripts"');
  });
});
