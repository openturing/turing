import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { TurSNSiteCustomSort } from "@/models/sn/sn-site-custom-sort.model";

/**
 * T1104 — a custom sort level says where documents missing its field go. The
 * contract is the payload: a placement the site picked is sent as FIRST/LAST, and a
 * level left on the engine default is sent with no placement at all.
 */

// Hoisted: the form instantiates the service at module scope.
const update = vi.hoisted(() => vi.fn());

vi.mock("@/services/sn/sn.site.custom.sort.service", () => ({
  TurSNSiteCustomSortService: class {
    getFieldOptions = () => Promise.resolve([{ id: "f1", name: "expiresAt", type: "DATE" }]);
    update = update;
    create = vi.fn();
  },
}));

import { SNSiteCustomSortForm } from "../sn.site.custom.sort.form";

function renderForm(value: TurSNSiteCustomSort) {
  return render(
    <MemoryRouter>
      <SNSiteCustomSortForm snSiteId="site-1" value={value} isNew={false} />
    </MemoryRouter>,
  );
}

async function submitAndReadPayload(): Promise<TurSNSiteCustomSort> {
  fireEvent.click(screen.getAllByText("forms.formActions.saveChanges")[0]);
  await waitFor(() => expect(update).toHaveBeenCalled());
  return update.mock.calls[0][1];
}

const closingSoonest: TurSNSiteCustomSort = {
  id: "sort-1",
  name: "closingSoonest",
  items: [{ id: "i1", fieldName: "expiresAt", sortOrder: "ASC", position: 0, missing: "LAST" }],
};

describe("SNSiteCustomSortForm — missing values placement (T1104)", () => {
  beforeEach(() => {
    update.mockReset();
    update.mockResolvedValue({ id: "sort-1" });
  });

  it("shows the stored placement of a level", () => {
    renderForm(closingSoonest);

    // The trigger, not the hidden native select Radix renders with every option.
    expect(screen.getByRole("combobox", { name: "sn.customSort.missing" }))
      .toHaveTextContent("sn.customSort.missingLast");
  });

  it("submits the stored placement unchanged", async () => {
    renderForm(closingSoonest);

    const payload = await submitAndReadPayload();

    expect(payload.items[0].missing).toBe("LAST");
  });

  it("shows the engine default for a level saved before placements existed", () => {
    renderForm({
      ...closingSoonest,
      items: [{ id: "i1", fieldName: "expiresAt", sortOrder: "ASC", position: 0 }],
    });

    expect(screen.getByRole("combobox", { name: "sn.customSort.missing" }))
      .toHaveTextContent("sn.customSort.missingDefault");
  });
});
