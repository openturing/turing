import { fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { TurSkill } from "@/models/skill/skill.model";

// ── Hook mocks (the CRUD page + form dialog read only these) ──
const uninstallMutate = vi.fn();
const installMutate = vi.fn();

let skillsData: TurSkill[] = [];

vi.mock("@/api/queries/skill.queries", () => ({
  useSkills: () => ({ data: skillsData, isError: false }),
  useUninstallSkill: () => ({ mutate: uninstallMutate, isPending: false }),
  useSkill: () => ({ data: undefined }),
  useInstallSkill: () => ({ mutate: installMutate, isPending: false }),
}));

// toast isn't globally mocked — override just `toast`, keep the real DS exports
// (Card/Button/Input/… re-export from this module).
vi.mock("@viglet/viglet-design-system", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@viglet/viglet-design-system")>();
  return { ...actual, toast: { success: vi.fn(), error: vi.fn() } };
});

import SkillListPage from "../skill.list.page";

function skill(overrides: Partial<TurSkill> = {}): TurSkill {
  return {
    id: "id-1",
    name: "scaffold-markdown",
    version: "1.0.0",
    author: "viglet",
    description: "Beat-structured markdown scaffolds.",
    signature: "sha256:abc",
    builtIn: true,
    enabled: true,
    tools: ["scaffold.markdown"],
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <SkillListPage />
    </MemoryRouter>,
  );
}

describe("SkillListPage (CRUD)", () => {
  beforeEach(() => {
    uninstallMutate.mockClear();
    installMutate.mockClear();
    skillsData = [];
  });

  it("lists installed skills", () => {
    skillsData = [skill(), skill({ id: "id-2", name: "my-skill", builtIn: false })];
    renderPage();
    expect(screen.getByText("scaffold-markdown")).toBeInTheDocument();
    expect(screen.getByText("my-skill")).toBeInTheDocument();
  });

  it("locks built-in skills (no edit/delete, platform-managed)", () => {
    skillsData = [skill({ builtIn: true })];
    renderPage();
    const card = screen.getByText("scaffold-markdown").closest("div.group") as HTMLElement;
    expect(within(card).getByText("skill.platformManaged")).toBeInTheDocument();
    expect(within(card).queryByText("skill.edit")).not.toBeInTheDocument();
  });

  it("offers edit + delete on user skills", () => {
    skillsData = [skill({ id: "id-2", name: "my-skill", builtIn: false })];
    renderPage();
    const card = screen.getByText("my-skill").closest("div.group") as HTMLElement;
    expect(within(card).getByText("skill.edit")).toBeInTheDocument();
    expect(within(card).getByRole("button", { name: "skill.uninstall" })).toBeInTheDocument();
  });

  it("opens the create form from the New Skill button", () => {
    skillsData = [skill()];
    renderPage();
    // toolbar New button
    fireEvent.click(screen.getAllByText("skill.new")[0]);
    expect(screen.getByText("skill.newTitle")).toBeInTheDocument();
  });

  it("confirms then uninstalls a user skill", () => {
    skillsData = [skill({ id: "id-2", name: "my-skill", builtIn: false })];
    renderPage();
    fireEvent.click(screen.getByRole("button", { name: "skill.uninstall" }));
    // confirmation dialog appears
    expect(screen.getByText("skill.deleteTitle")).toBeInTheDocument();
    // the dialog's confirm button carries the uninstall label text
    const dialog = screen.getByRole("dialog");
    fireEvent.click(within(dialog).getByText("skill.uninstall"));
    expect(uninstallMutate).toHaveBeenCalledTimes(1);
    expect(uninstallMutate.mock.calls[0][0]).toBe("id-2");
  });

  it("filters by search query", () => {
    skillsData = [skill(), skill({ id: "id-2", name: "my-skill", builtIn: false })];
    renderPage();
    fireEvent.change(screen.getByPlaceholderText("skill.searchPlaceholder"), {
      target: { value: "my-skill" },
    });
    expect(screen.queryByText("scaffold-markdown")).not.toBeInTheDocument();
    expect(screen.getByText("my-skill")).toBeInTheDocument();
  });
});
