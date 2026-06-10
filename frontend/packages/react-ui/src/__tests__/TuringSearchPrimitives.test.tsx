import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { TuringSearchBar } from "../TuringSearchBar";
import { TuringResultList, type TuringResolvedDocument } from "../TuringResultList";
import { TuringPagination, type TuringPaginationItemData } from "../TuringPagination";

/**
 * T306 — headless-contract render tests for the search primitives migrated from
 * `@viglet/turing-react-sdk`. They assert structure + callbacks only (no styling).
 */

function makeDoc(url: string, title: string): TuringResolvedDocument {
  return {
    url,
    title,
    description: "",
    date: "",
    image: "",
    text: "",
    raw: { elevate: false, fields: { title }, metadata: [] },
  };
}

describe("TuringSearchBar", () => {
  it("submits the typed query via onSearch", () => {
    const onSearch = vi.fn();
    render(<TuringSearchBar onSearch={onSearch} placeholder="Find" />);
    const input = screen.getByRole("searchbox");
    fireEvent.change(input, { target: { value: "hello" } });
    fireEvent.submit(input.closest("form")!);
    expect(onSearch).toHaveBeenCalledWith("hello");
  });

  it("submits '*' for an empty query", () => {
    const onSearch = vi.fn();
    render(<TuringSearchBar onSearch={onSearch} />);
    fireEvent.submit(screen.getByRole("search"));
    expect(onSearch).toHaveBeenCalledWith("*");
  });

  it("honors the renderInput / renderButton render props", () => {
    render(
      <TuringSearchBar
        onSearch={vi.fn()}
        renderInput={(props) => <input {...props} data-testid="custom-input" />}
        renderButton={(p) => <button onClick={p.onClick}>Go</button>}
      />,
    );
    expect(screen.getByTestId("custom-input")).toBeInTheDocument();
    expect(screen.getByText("Go")).toBeInTheDocument();
  });
});

describe("TuringResultList", () => {
  it("renders one listitem per document via itemComponent", () => {
    const docs = [makeDoc("/a", "Alpha"), makeDoc("/b", "Beta")];
    render(
      <TuringResultList
        documents={docs}
        itemComponent={({ document }) => <span>{document.title}</span>}
      />,
    );
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    expect(screen.getByText("Alpha")).toBeInTheDocument();
    expect(screen.getByText("Beta")).toBeInTheDocument();
  });

  it("renders the loading slot when isLoading", () => {
    render(
      <TuringResultList
        documents={[]}
        isLoading
        loadingComponent={() => <p>Loading…</p>}
        itemComponent={() => null}
      />,
    );
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("renders the empty slot when there are no documents", () => {
    render(
      <TuringResultList
        documents={[]}
        emptyComponent={() => <p>No results</p>}
        itemComponent={() => null}
      />,
    );
    expect(screen.getByText("No results")).toBeInTheDocument();
  });
});

describe("TuringPagination", () => {
  const items: TuringPaginationItemData[] = [
    { type: "PREVIOUS", page: 1, text: "previous", href: "/p=1" },
    { type: "CURRENT", page: 2, text: "2", href: "/p=2" },
    { type: "PAGE", page: 3, text: "3", href: "/p=3" },
  ];

  it("renders nothing when there are no items", () => {
    const { container } = render(<TuringPagination items={[]} onNavigate={vi.fn()} />);
    expect(container.querySelector("nav")).toBeNull();
  });

  it("navigates on click but not for the current page", () => {
    const onNavigate = vi.fn();
    render(<TuringPagination items={items} onNavigate={onNavigate} />);
    fireEvent.click(screen.getByLabelText("Page 3"));
    expect(onNavigate).toHaveBeenCalledWith("/p=3");
    // CURRENT is disabled — clicking it must not navigate.
    fireEvent.click(screen.getByLabelText("Page 2"));
    expect(onNavigate).toHaveBeenCalledTimes(1);
  });
});
