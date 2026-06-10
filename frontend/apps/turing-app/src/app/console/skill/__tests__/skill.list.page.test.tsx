import { fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { TurSkill, TurSkillManifest } from "@/models/skill/skill.model";

// ── Hook mocks (the page reads only these; no axios / QueryClient needed) ──
const installMutate = vi.fn();
const uninstallMutate = vi.fn();

let installedData: TurSkill[] = [];
let catalogData: TurSkillManifest[] = [];

vi.mock("@/api/queries/skill.queries", () => ({
  useSkills: () => ({ data: installedData, isError: false }),
  useSkillCatalog: () => ({ data: catalogData, isError: false }),
  useInstallSkillFromCatalog: () => ({ mutate: installMutate, isPending: false, variables: undefined }),
  useUninstallSkill: () => ({ mutate: uninstallMutate, isPending: false, variables: undefined }),
}));

// toast isn't globally mocked — override just `toast` while keeping the real
// design-system exports (Card/Button/Input/… re-export from this module).
vi.mock("@viglet/viglet-design-system", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@viglet/viglet-design-system")>();
  return { ...actual, toast: { success: vi.fn(), error: vi.fn() } };
});

import SkillListPage from "../skill.list.page";

const markdownManifest: TurSkillManifest = {
  name: "scaffold-markdown",
  version: "1.0.0",
  author: "viglet",
  description: "Beat-structured markdown scaffolds.",
  tools: ["scaffold.markdown"],
};

const mindmapManifest: TurSkillManifest = {
  name: "scaffold-mindmap",
  version: "1.0.0",
  author: "viglet",
  description: "Mermaid mind-map trees.",
  tools: ["scaffold.buildMindMap"],
};

function installedFrom(manifest: TurSkillManifest): TurSkill {
  return {
    id: `id-${manifest.name}`,
    name: manifest.name,
    version: manifest.version,
    author: manifest.author ?? null,
    description: manifest.description ?? null,
    signature: "sha256:abc",
    builtIn: true,
    enabled: true,
    tools: manifest.tools ?? null,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <SkillListPage />
    </MemoryRouter>,
  );
}

describe("SkillListPage", () => {
  beforeEach(() => {
    installMutate.mockClear();
    uninstallMutate.mockClear();
    installedData = [];
    catalogData = [];
  });

  it("renders catalog skills with an install button when not installed", () => {
    catalogData = [markdownManifest, mindmapManifest];
    renderPage();

    expect(screen.getByText("scaffold-markdown")).toBeInTheDocument();
    expect(screen.getByText("scaffold-mindmap")).toBeInTheDocument();
    // two available skills → two install buttons
    expect(screen.getAllByText("skill.install").length).toBe(2);
  });

  it("marks installed skills and offers details instead of install", () => {
    catalogData = [markdownManifest];
    installedData = [installedFrom(markdownManifest)];
    renderPage();

    const card = screen.getByText("scaffold-markdown").closest("div.group") as HTMLElement;
    expect(within(card).getByText("skill.installed")).toBeInTheDocument();
    // installed → no per-card install button, only the details button
    expect(within(card).queryByText("skill.install")).not.toBeInTheDocument();
    expect(within(card).getByText("skill.details")).toBeInTheDocument();
  });

  it("filters by search query", () => {
    catalogData = [markdownManifest, mindmapManifest];
    renderPage();

    fireEvent.change(screen.getByPlaceholderText("skill.searchPlaceholder"), {
      target: { value: "mindmap" },
    });

    expect(screen.queryByText("scaffold-markdown")).not.toBeInTheDocument();
    expect(screen.getByText("scaffold-mindmap")).toBeInTheDocument();
  });

  it("installs from the catalog when the install button is clicked", () => {
    catalogData = [markdownManifest];
    renderPage();

    fireEvent.click(screen.getByText("skill.install"));
    expect(installMutate).toHaveBeenCalledTimes(1);
    expect(installMutate.mock.calls[0][0]).toBe("scaffold-markdown");
  });

  it("shows the empty state when nothing matches", () => {
    catalogData = [markdownManifest];
    renderPage();

    fireEvent.change(screen.getByPlaceholderText("skill.searchPlaceholder"), {
      target: { value: "no-such-skill" },
    });
    expect(screen.getByText("skill.emptyTitle")).toBeInTheDocument();
  });
});
