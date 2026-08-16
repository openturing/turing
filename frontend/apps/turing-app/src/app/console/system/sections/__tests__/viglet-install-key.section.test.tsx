import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { VigletInstallKeySection } from "../viglet-install-key.section";

// `vi.mock` factories hoist above the module body, so the spies hoist with them.
const { status, rotateKey } = vi.hoisted(() => ({
  status: vi.fn(),
  rotateKey: vi.fn(),
}));

vi.mock("@/services/genai/viglet-ai.service", () => ({
  TurVigletAiService: class {
    status = status;
    rotateKey = rotateKey;
  },
}));

const PAIRED = {
  paired: true,
  llmInstanceId: "managed-1",
  defaultLlm: true,
  installId: "install-uuid",
};

describe("VigletInstallKeySection (T965)", () => {
  beforeEach(() => {
    status.mockReset();
    rotateKey.mockReset();
  });

  it("renders nothing on an install that never paired", async () => {
    status.mockResolvedValue({ ...PAIRED, paired: false });

    const { container } = render(<VigletInstallKeySection />);

    await waitFor(() => expect(status).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it("offers the rotation once the install is bound", async () => {
    status.mockResolvedValue(PAIRED);

    render(<VigletInstallKeySection />);

    expect(await screen.findByRole("button", { name: /Rotate key/ })).toBeInTheDocument();
  });

  it("reports a refusal as unchanged rather than as a failure", async () => {
    status.mockResolvedValue(PAIRED);
    rotateKey.mockResolvedValue({
      rotated: false,
      publicKey: "old-pub-key",
      detail: "Viglet AI refused the new key: unknown_install. The current key is unchanged.",
    });

    render(<VigletInstallKeySection />);
    (await screen.findByRole("button", { name: /Rotate key/ })).click();

    expect(await screen.findByText(/Unchanged/)).toBeInTheDocument();
    expect(screen.getByText(/current key is unchanged/)).toBeInTheDocument();
  });

  it("says nothing was rotated when the endpoint cannot be reached", async () => {
    status.mockResolvedValue(PAIRED);
    rotateKey.mockRejectedValue(new Error("network"));

    render(<VigletInstallKeySection />);
    (await screen.findByRole("button", { name: /Rotate key/ })).click();

    expect(await screen.findByText(/could not be reached/)).toBeInTheDocument();
  });

  it("confirms a completed rotation", async () => {
    status.mockResolvedValue(PAIRED);
    rotateKey.mockResolvedValue({
      rotated: true,
      publicKey: "new-pub-key",
      detail: "The install signing key was rotated.",
    });

    render(<VigletInstallKeySection />);
    (await screen.findByRole("button", { name: /Rotate key/ })).click();

    expect(await screen.findByText(/Rotated/)).toBeInTheDocument();
  });
});
