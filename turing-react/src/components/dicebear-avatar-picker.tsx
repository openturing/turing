import { IconCheck, IconDice5, IconPalette } from "@tabler/icons-react"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils"

const DICEBEAR_STYLES = [
  { id: "adventurer", label: "Adventurer" },
  { id: "avataaars", label: "Avataaars" },
  { id: "big-ears", label: "Big Ears" },
  { id: "big-smile", label: "Big Smile" },
  { id: "bottts", label: "Bottts" },
  { id: "croodles", label: "Croodles" },
  { id: "dylan", label: "Dylan" },
  { id: "fun-emoji", label: "Fun Emoji" },
  { id: "glass", label: "Glass" },
  { id: "icons", label: "Icons" },
  { id: "identicon", label: "Identicon" },
  { id: "lorelei", label: "Lorelei" },
  { id: "micah", label: "Micah" },
  { id: "miniavs", label: "Miniavs" },
  { id: "notionists", label: "Notionists" },
  { id: "open-peeps", label: "Open Peeps" },
  { id: "personas", label: "Personas" },
  { id: "pixel-art", label: "Pixel Art" },
  { id: "rings", label: "Rings" },
  { id: "shapes", label: "Shapes" },
  { id: "thumbs", label: "Thumbs" },
] as const

type DiceBearStyle = (typeof DICEBEAR_STYLES)[number]["id"]

function buildDiceBearUrl(style: string, seed: string): string {
  return `https://api.dicebear.com/9.x/${style}/svg?seed=${encodeURIComponent(seed)}`
}

function generateRandomSeed(): string {
  return Math.random().toString(36).substring(2, 10)
}

function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedValue(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return debouncedValue
}

interface DiceBearAvatarPickerProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSelect: (avatarUrl: string) => void
  defaultSeed?: string
  currentAvatarUrl?: string
}

export function DiceBearAvatarPicker({
  open,
  onOpenChange,
  onSelect,
  defaultSeed = "",
  currentAvatarUrl,
}: DiceBearAvatarPickerProps) {
  const [selectedStyle, setSelectedStyle] = useState<DiceBearStyle>("adventurer")
  const [seed, setSeed] = useState(defaultSeed || generateRandomSeed())
  const [previewSeeds, setPreviewSeeds] = useState<string[]>([])
  const [selectedUrl, setSelectedUrl] = useState("")
  const imgKeyRef = useRef(0)

  const debouncedSeed = useDebounce(seed, 400)

  useEffect(() => {
    if (open) {
      if (currentAvatarUrl) {
        const match = currentAvatarUrl.match(/dicebear\.com\/[\d.]+x\/([^/]+)\/svg\?seed=([^&]+)/)
        if (match) {
          setSelectedStyle(match[1] as DiceBearStyle)
          setSeed(decodeURIComponent(match[2]))
        }
      }
      generatePreviewSeeds()
    }
  }, [open])

  const generatePreviewSeeds = useCallback(() => {
    const seeds = Array.from({ length: 12 }, () => generateRandomSeed())
    setPreviewSeeds(seeds)
    imgKeyRef.current += 1
  }, [])

  const mainPreviewUrl = useMemo(
    () => buildDiceBearUrl(selectedStyle, debouncedSeed),
    [selectedStyle, debouncedSeed]
  )

  useEffect(() => {
    setSelectedUrl(mainPreviewUrl)
  }, [mainPreviewUrl])

  const handleRandomize = () => {
    setSeed(generateRandomSeed())
    generatePreviewSeeds()
  }

  const handleConfirm = () => {
    onSelect(selectedUrl)
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <IconPalette className="size-5" />
            Choose Your Avatar
          </DialogTitle>
          <DialogDescription>
            Pick a style and customize your unique avatar powered by DiceBear.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-5">
          {/* Main preview */}
          <div className="flex justify-center">
            <div className="relative">
              <div className="size-28 rounded-full overflow-hidden border-4 border-primary/20 bg-muted shadow-lg">
                <img
                  src={selectedUrl}
                  alt="Avatar preview"
                  className="size-full object-cover"
                />
              </div>
              {selectedUrl === currentAvatarUrl && (
                <div className="absolute -bottom-1 -right-1 bg-green-500 text-white rounded-full p-1">
                  <IconCheck className="size-3" />
                </div>
              )}
            </div>
          </div>

          {/* Seed input + randomize */}
          <div className="flex items-end gap-2">
            <div className="flex-1 space-y-1.5">
              <Label htmlFor="seed">Seed</Label>
              <Input
                id="seed"
                value={seed}
                onChange={(e) => setSeed(e.target.value)}
                placeholder="Type a name or word..."
              />
            </div>
            <Button variant="outline" size="icon" onClick={handleRandomize} title="Randomize">
              <IconDice5 className="size-4" />
            </Button>
          </div>

          {/* Style selector */}
          <div className="space-y-2">
            <Label>Style</Label>
            <div className="grid grid-cols-7 gap-2 max-h-48 overflow-y-auto pr-1">
              {DICEBEAR_STYLES.map((style) => {
                const url = buildDiceBearUrl(style.id, debouncedSeed)
                const isSelected = style.id === selectedStyle
                return (
                  <button
                    key={style.id}
                    type="button"
                    onClick={() => {
                      setSelectedStyle(style.id)
                      setSelectedUrl(url)
                    }}
                    className={cn(
                      "flex flex-col items-center gap-1 p-1.5 rounded-lg border-2 transition-all cursor-pointer hover:bg-accent",
                      isSelected
                        ? "border-primary bg-primary/5 shadow-sm"
                        : "border-transparent"
                    )}
                    title={style.label}
                  >
                    <div className="size-10 rounded-full overflow-hidden bg-muted">
                      <img
                        key={`${style.id}-${debouncedSeed}`}
                        src={url}
                        alt={style.label}
                        className="size-full object-cover"
                        loading="lazy"
                      />
                    </div>
                    <span className="text-[10px] leading-tight text-center truncate w-full">
                      {style.label}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>

          {/* Seed variations grid */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label>Variations</Label>
              <Button
                variant="ghost"
                size="sm"
                className="h-7 text-xs"
                onClick={generatePreviewSeeds}
              >
                <IconDice5 className="size-3 mr-1" />
                Shuffle
              </Button>
            </div>
            <div className="grid grid-cols-6 gap-2">
              {previewSeeds.map((s) => {
                const url = buildDiceBearUrl(selectedStyle, s)
                const isActive = url === selectedUrl
                return (
                  <button
                    key={`${s}-${imgKeyRef.current}`}
                    type="button"
                    onClick={() => {
                      setSelectedUrl(url)
                    }}
                    className={cn(
                      "aspect-square rounded-xl overflow-hidden border-2 transition-all cursor-pointer hover:scale-105 hover:shadow-md bg-muted",
                      isActive
                        ? "border-primary ring-2 ring-primary/30 shadow-md"
                        : "border-transparent hover:border-muted-foreground/20"
                    )}
                  >
                    <img
                      src={url}
                      alt={`Variation ${s}`}
                      className="size-full object-cover"
                      loading="lazy"
                    />
                  </button>
                )
              })}
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleConfirm}>
            Use This Avatar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
