import { IconDeviceFloppy, IconLoader2, IconLock, IconPalette, IconTrash } from "@tabler/icons-react"
import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"

import { DiceBearAvatarPicker } from "@/components/dicebear-avatar-picker"
import { Button } from "@/components/ui/button"
import { GradientAvatar, GradientAvatarFallback, GradientAvatarImage } from "@/components/ui/gradient-avatar"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import type { TurUser } from "@/models/auth/user"
import { TurUserService } from "@/services/auth/user.service"

const turUserService = new TurUserService()

export default function UserAccountPage() {
  const [user, setUser] = useState<TurUser | null>(null)
  const [firstName, setFirstName] = useState("")
  const [lastName, setLastName] = useState("")
  const [email, setEmail] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(true)
  const [avatarUrl, setAvatarUrl] = useState<string | undefined>(undefined)
  const [pickerOpen, setPickerOpen] = useState(false)

  useEffect(() => {
    turUserService.get().then((currentUser) => {
      setUser(currentUser)
      setFirstName(currentUser.firstName ?? "")
      setLastName(currentUser.lastName ?? "")
      setEmail(currentUser.email ?? "")
      setAvatarUrl(currentUser.avatarUrl ?? undefined)
      setLoading(false)
    }).catch(() => {
      toast.error("Failed to load user data.")
      setLoading(false)
    })
  }, [])

  const initials = (() => {
    const first = firstName || ""
    const last = lastName || ""
    if (!first && !last) return " "
    const parts = `${first} ${last}`.trim().split(" ").filter(Boolean)
    if (parts.length === 1) return parts[0].charAt(0).toUpperCase()
    return parts[0].charAt(0).toUpperCase() + (parts.at(-1)?.charAt(0).toUpperCase() ?? "")
  })()

  const handleAvatarSelect = useCallback(async (url: string) => {
    if (!user) return
    try {
      await turUserService.saveAvatarUrl(user.username, url)
      setAvatarUrl(url)
      toast.success("Avatar updated.")
    } catch {
      toast.error("Failed to save avatar.")
    }
  }, [user])

  const handleAvatarRemove = useCallback(async () => {
    if (!user) return
    try {
      await turUserService.saveAvatarUrl(user.username, null)
      setAvatarUrl(undefined)
      toast.success("Avatar removed.")
    } catch {
      toast.error("Failed to remove avatar.")
    }
  }, [user])

  const handleSave = async () => {
    if (!user) return
    if (newPassword && newPassword !== confirmPassword) {
      toast.error("Passwords do not match.")
      return
    }
    setSaving(true)
    try {
      const payload: Partial<TurUser> = {
        firstName,
        lastName,
        email,
      }
      if (newPassword) {
        payload.password = newPassword
      }
      await turUserService.update(user.username, payload)
      toast.success("Account updated successfully.")
      setNewPassword("")
      setConfirmPassword("")
    } catch {
      toast.error("Failed to update account.")
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <IconLoader2 className="size-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (!user) {
    return (
      <div className="flex items-center justify-center h-full text-muted-foreground">
        Unable to load user data.
      </div>
    )
  }

  return (
    <div className="max-w-2xl mx-auto py-8 px-6">
      <h1 className="text-2xl font-semibold mb-6">Account Settings</h1>

      {/* Avatar + username */}
      <div className="flex items-center gap-4 mb-8">
        <div className="relative group">
          <GradientAvatar className="size-16">
            {avatarUrl
              ? <GradientAvatarImage src={avatarUrl} alt={user.username} />
              : null
            }
            <GradientAvatarFallback className="text-lg">{initials}</GradientAvatarFallback>
          </GradientAvatar>
          <button
            type="button"
            title="Choose avatar"
            onClick={() => setPickerOpen(true)}
            className="absolute inset-0 flex items-center justify-center rounded-full bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
          >
            <IconPalette className="size-5 text-white" />
          </button>
        </div>
        <div>
          <div className="text-lg font-medium">{firstName} {lastName}</div>
          <div className="text-sm text-muted-foreground">@{user.username}</div>
          <div className="flex items-center gap-2 mt-1">
            <button
              type="button"
              onClick={() => setPickerOpen(true)}
              className="text-xs text-primary hover:underline cursor-pointer"
            >
              Choose avatar
            </button>
            {avatarUrl && (
              <button
                type="button"
                onClick={handleAvatarRemove}
                className="text-xs text-destructive hover:underline cursor-pointer flex items-center gap-0.5"
              >
                <IconTrash className="size-3" />
                Remove
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Profile fields */}
      <div className="space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="firstName">First Name</Label>
            <Input id="firstName" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="lastName">Last Name</Label>
            <Input id="lastName" value={lastName} onChange={(e) => setLastName(e.target.value)} />
          </div>
        </div>
        <div className="space-y-2">
          <Label htmlFor="email">Email</Label>
          <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="username">Username</Label>
          <Input id="username" value={user.username} disabled className="bg-muted" />
        </div>
      </div>

      <Separator className="my-8" />

      {/* Change password */}
      <h2 className="text-lg font-medium mb-4 flex items-center gap-2">
        <IconLock className="size-5" />
        Change Password
      </h2>
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="newPassword">New Password</Label>
          <Input id="newPassword" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="Leave blank to keep current" />
        </div>
        <div className="space-y-2">
          <Label htmlFor="confirmPassword">Confirm Password</Label>
          <Input id="confirmPassword" type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder="Repeat new password" />
        </div>
      </div>

      <div className="mt-8">
        <Button onClick={handleSave} disabled={saving}>
          {saving ? <IconLoader2 className="size-4 animate-spin mr-2" /> : <IconDeviceFloppy className="size-4 mr-2" />}
          Save Changes
        </Button>
      </div>

      {/* DiceBear Avatar Picker Dialog */}
      <DiceBearAvatarPicker
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        onSelect={handleAvatarSelect}
        defaultSeed={user.username}
        currentAvatarUrl={avatarUrl}
      />
    </div>
  )
}
