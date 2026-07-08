import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ROUTES } from "@/app/routes.const";
import { useFeatures } from "@/api/queries/features.queries";
import { useCurrentUser } from "@/contexts/user.context";
import { IconBuildingCommunity, IconBuildingSkyscraper, IconKeyboard, IconLogout, IconSparkles, IconUserCircle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { UserAvatar } from "@viglet/viglet-design-system";

/**
 * User avatar + dropdown styled to harmonize with the Bento UI.
 *
 * Uses the same Radix DropdownMenu primitives as the design-system
 * `UserMenu`, but the surface (`.bento-dropdown`) and items
 * (`.bento-dropdown-item`) carry the frosted-glass look and iOS spring
 * easing so it feels like part of the bento language.
 */
export interface BentoUserMenuProps {
  /** Open the keyboard-shortcut guide (T572). Omit to hide the entry. */
  onOpenShortcuts?: () => void;
  /** Replay the first-run tour (T572). Omit to hide the entry. */
  onReplayTour?: () => void;
}

export function BentoUserMenu({ onOpenShortcuts, onReplayTour }: Readonly<BentoUserMenuProps> = {}) {
  const { user } = useCurrentUser();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data: features } = useFeatures();
  const tenancyEnabled = features?.tenancyEnabled === true;
  const platformAdmin = features?.platformAdmin === true;

  const fullName =
    `${user.firstName ?? ""} ${user.lastName ?? ""}`.trim() ||
    user.username ||
    "";

  // While the user is still loading (empty state), keep the slot reserved
  // so the header doesn't reflow when the avatar lands.
  if (!user.username) {
    return <div className="h-9 w-9 rounded-full bg-muted/40" aria-hidden />;
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          title={fullName || user.username}
          className="bento-tile-clickable cursor-pointer rounded-full p-0.5 ring-1 ring-border/60 outline-none transition-shadow duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] hover:ring-2 hover:ring-indigo-500/50 focus-visible:ring-2 focus-visible:ring-ring"
        >
          <UserAvatar
            givenName={user.firstName}
            familyName={user.lastName}
            name={fullName || user.username}
            src={user.avatarUrl || undefined}
            alt={user.username}
          />
        </button>
      </DropdownMenuTrigger>

      <DropdownMenuContent
        align="end"
        sideOffset={10}
        className="bento-dropdown min-w-64 p-2"
      >
        <DropdownMenuLabel className="p-0 font-normal">
          <div className="flex items-center gap-3 rounded-xl px-2 py-2 text-left text-sm">
            <span className="rounded-full p-0.5 ring-1 ring-border/60">
              <UserAvatar
                givenName={user.firstName}
                familyName={user.lastName}
                name={fullName || user.username}
                src={user.avatarUrl || undefined}
                alt={user.username}
              />
            </span>
            <div className="grid flex-1 text-left text-sm leading-tight">
              {fullName && <span className="truncate font-medium">{fullName}</span>}
              {user.email && (
                <span className="truncate text-xs text-muted-foreground">{user.email}</span>
              )}
            </div>
          </div>
        </DropdownMenuLabel>

        <DropdownMenuSeparator className="my-1.5 bg-border/50" />

        <DropdownMenuGroup>
          <DropdownMenuItem
            onSelect={() => navigate(ROUTES.BENTO_USER_ACCOUNT)}
            className="bento-dropdown-item cursor-pointer gap-2 px-3 py-2"
          >
            <IconUserCircle size={18} />
            {t("nav.account")}
          </DropdownMenuItem>
          {tenancyEnabled && (
            <DropdownMenuItem
              onSelect={() => navigate(ROUTES.BENTO_TENANT_SETTINGS)}
              className="bento-dropdown-item cursor-pointer gap-2 px-3 py-2"
            >
              <IconBuildingCommunity size={18} />
              {t("nav.organizations", { defaultValue: "Organizations" })}
            </DropdownMenuItem>
          )}
          {tenancyEnabled && platformAdmin && (
            <DropdownMenuItem
              onSelect={() => navigate(ROUTES.BENTO_TENANT_ADMIN)}
              className="bento-dropdown-item cursor-pointer gap-2 px-3 py-2"
            >
              <IconBuildingSkyscraper size={18} />
              {t("nav.tenantAdmin", { defaultValue: "Tenant administration" })}
            </DropdownMenuItem>
          )}
        </DropdownMenuGroup>

        {(onOpenShortcuts || onReplayTour) && (
          <>
            <DropdownMenuSeparator className="my-1.5 bg-border/50" />
            <DropdownMenuGroup>
              {onReplayTour && (
                <DropdownMenuItem
                  onSelect={() => onReplayTour()}
                  className="bento-dropdown-item cursor-pointer gap-2 px-3 py-2"
                >
                  <IconSparkles size={18} />
                  {t("bento.tour.replay", { defaultValue: "Take the tour" })}
                </DropdownMenuItem>
              )}
              {onOpenShortcuts && (
                <DropdownMenuItem
                  onSelect={() => onOpenShortcuts()}
                  className="bento-dropdown-item cursor-pointer gap-2 px-3 py-2"
                >
                  <IconKeyboard size={18} />
                  {t("bento.shortcuts.title", { defaultValue: "Keyboard shortcuts" })}
                </DropdownMenuItem>
              )}
            </DropdownMenuGroup>
          </>
        )}

        <DropdownMenuSeparator className="my-1.5 bg-border/50" />

        <DropdownMenuItem
          onSelect={() => { globalThis.location.href = ROUTES.LOGOUT; }}
          className="bento-dropdown-item cursor-pointer gap-2 px-3 py-2 text-rose-600 focus:bg-rose-500/10 focus:text-rose-600 dark:text-rose-400 dark:focus:text-rose-300"
        >
          <IconLogout size={18} />
          {t("nav.logOut")}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
