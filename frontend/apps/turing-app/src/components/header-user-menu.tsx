import { useNavigate } from "react-router-dom";
import { UserMenu } from "@viglet/viglet-design-system";

import { ROUTES } from "@/app/routes.const";
import type { TurUser } from "@/models/auth/user";

interface HeaderUserMenuProps {
  user: TurUser;
}

/**
 * Thin wrapper over the design-system UserMenu that wires Turing's
 * navigation and logout routes.
 */
export function HeaderUserMenu({ user }: Readonly<HeaderUserMenuProps>) {
  const navigate = useNavigate();

  return (
    <UserMenu
      user={{
        username: user.username,
        firstName: user.firstName,
        lastName: user.lastName,
        email: user.email,
        avatarUrl: user.avatarUrl,
      }}
      onAccount={() => navigate(ROUTES.USER_ACCOUNT)}
      onSignOut={() => {
        globalThis.location.href = ROUTES.LOGOUT;
      }}
    />
  );
}
