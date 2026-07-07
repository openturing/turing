import { act, render, renderHook, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  TuringGenerativeContent,
  useGenerativeUI,
  type TuringGenerativeComponentProps,
} from "@viglet/turing-react-sdk";

/**
 * T440 — tool-driven generative UI: the headless `TuringGenerativeContent`
 * renderer (name → component dispatch) + the `useGenerativeUI` hook that turns a
 * registry into client-tool handlers and resolves them on `respond`.
 */
function PriceTable({ props, respond }: TuringGenerativeComponentProps) {
  return (
    <div>
      <span>price: {String((props as { price?: number }).price)}</span>
      <button type="button" onClick={() => respond({ chosen: true })}>
        choose
      </button>
    </div>
  );
}

describe("Generative UI (T440)", () => {
  it("renders a registered component and routes respond with the item id", async () => {
    const onRespond = vi.fn();
    render(
      <TuringGenerativeContent
        items={[{ id: "c1", component: "price_table", props: { price: 42 } }]}
        registry={{ price_table: PriceTable }}
        onRespond={onRespond}
      />,
    );
    expect(screen.getByText("price: 42")).toBeInTheDocument();
    await userEvent.click(screen.getByText("choose"));
    expect(onRespond).toHaveBeenCalledWith("c1", { chosen: true });
  });

  it("renders nothing for an unknown component without renderUnknown", () => {
    const { container } = render(
      <TuringGenerativeContent
        items={[{ id: "c1", component: "missing" }]}
        registry={{}}
        onRespond={() => {}}
      />,
    );
    expect(container.querySelector('[data-component="missing"]')).toBeNull();
  });

  it("useGenerativeUI: a handler renders an item and respond resolves its parked call", async () => {
    const { result } = renderHook(() => useGenerativeUI({ picker: () => null }));
    expect(Object.keys(result.current.clientTools)).toEqual(["picker"]);

    let promise: Promise<unknown> | undefined;
    act(() => {
      promise = result.current.clientTools.picker({ a: 1 }) as Promise<unknown>;
    });
    expect(result.current.items).toHaveLength(1);
    const item = result.current.items[0];
    expect(item.component).toBe("picker");
    expect(item.props).toEqual({ a: 1 });

    act(() => result.current.respond(item.id, "done"));
    await expect(promise!).resolves.toBe("done");
    expect(result.current.items).toHaveLength(0);
  });

  it("useGenerativeUI: clear resolves pending calls with undefined and empties items", async () => {
    const { result } = renderHook(() => useGenerativeUI({ picker: () => null }));
    let promise: Promise<unknown> | undefined;
    act(() => {
      promise = result.current.clientTools.picker({}) as Promise<unknown>;
    });
    act(() => result.current.clear());
    await expect(promise!).resolves.toBeUndefined();
    expect(result.current.items).toHaveLength(0);
  });
});
