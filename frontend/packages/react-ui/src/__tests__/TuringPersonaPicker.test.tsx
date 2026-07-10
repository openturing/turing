import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { TuringPersonaPicker, type TuringPersonaOption } from "../TuringPersonaPicker";

const OPTIONS: TuringPersonaOption[] = [
  { id: "p1", name: "Skeptical Developer", description: "Wants proof" },
  { id: "p2", name: "Enterprise Buyer" },
];

describe("TuringPersonaPicker (T636)", () => {
  it("renders one radio per option under a radiogroup", () => {
    render(<TuringPersonaPicker options={OPTIONS} onChange={() => {}} />);
    const group = screen.getByRole("radiogroup");
    expect(group).toBeInTheDocument();
    expect(screen.getAllByRole("radio")).toHaveLength(2);
    expect(screen.getByText("Skeptical Developer")).toBeInTheDocument();
  });

  it("marks the selected option aria-checked and data-active", () => {
    render(<TuringPersonaPicker options={OPTIONS} value="p2" onChange={() => {}} />);
    const selected = screen.getByRole("radio", { name: /Enterprise Buyer/ });
    expect(selected).toHaveAttribute("aria-checked", "true");
    expect(selected).toHaveAttribute("data-active", "");
  });

  it("fires onChange with the persona id", () => {
    const onChange = vi.fn();
    render(<TuringPersonaPicker options={OPTIONS} onChange={onChange} />);
    fireEvent.click(screen.getByText("Skeptical Developer"));
    expect(onChange).toHaveBeenCalledWith("p1");
  });

  it("includeDefault adds an option that selects null", () => {
    const onChange = vi.fn();
    render(
      <TuringPersonaPicker
        options={OPTIONS}
        value={null}
        includeDefault
        onChange={onChange}
        labels={{ defaultOption: "Default voice" }}
      />,
    );
    const def = screen.getByRole("radio", { name: "Default voice" });
    expect(def).toHaveAttribute("aria-checked", "true");
    fireEvent.click(screen.getByText("Enterprise Buyer"));
    expect(onChange).toHaveBeenCalledWith("p2");
    fireEvent.click(def);
    expect(onChange).toHaveBeenCalledWith(null);
  });

  it("renders nothing when there are no options and no default", () => {
    const { container } = render(<TuringPersonaPicker options={[]} onChange={() => {}} />);
    expect(container.querySelector("[data-turing-persona-picker]")).toBeNull();
  });
});
