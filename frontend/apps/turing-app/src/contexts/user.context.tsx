// T997 -- the export-manifest lint runs in CI now, and these names are also
// exported by @viglet/viglet-design-system. Allowed until T1000: console-era, and 40 console pages still render it.
// viglet-ds-allow-duplicate UserProvider -- T1000 removes it
// viglet-ds-allow-duplicate useCurrentUser -- T1000 removes it
import type { TurUser } from "@/models/auth/user"
import { TurUserService } from "@/services/auth/user.service"
import React from "react"

interface UserContextValue {
  user: TurUser
  refreshUser: () => void
}

const UserContext = React.createContext<UserContextValue | null>(null)

const turUserService = new TurUserService()

export function UserProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = React.useState<TurUser>({} as TurUser)

  const refreshUser = React.useCallback(() => {
    turUserService.get().then(setUser)
  }, [])

  React.useEffect(() => {
    refreshUser()
  }, [refreshUser])

  const value = React.useMemo(() => ({ user, refreshUser }), [user, refreshUser])

  return <UserContext value={value}>{children}</UserContext>
}

export function useCurrentUser(): UserContextValue {
  const ctx = React.useContext(UserContext)
  if (!ctx) throw new Error("useCurrentUser must be used within UserProvider")
  return ctx
}
