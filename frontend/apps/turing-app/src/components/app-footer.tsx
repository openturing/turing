import { TurLogo } from "@/components/logo/tur-logo"
import { TurSystemInfoService } from "@/services/system/system-info.service"
import { useEffect, useState } from "react"

const turSystemInfoService = new TurSystemInfoService()

export function AppFooter() {
  const [version, setVersion] = useState<string>("")

  useEffect(() => {
    turSystemInfoService.getInfo()
      .then((info) => setVersion(info.appVersion ?? ""))
      .catch(() => setVersion(""))
  }, [])

  return (
    <footer className="mt-auto">
      <div className="mx-6 border-t" />
      <div className="px-6 py-3 flex items-center justify-center gap-2 flex-wrap">
        <TurLogo className="size-4 opacity-50" />
        <span className="text-xs text-muted-foreground/70">
          Viglet Turing ES{version ? ` ${version}` : ""}
        </span>
        <span className="text-xs text-muted-foreground/40">·</span>
        <a href="https://docs.viglet.org/turing/" target="_blank" rel="noopener noreferrer" className="text-[11px] text-muted-foreground/50 hover:text-muted-foreground transition-colors">
          Docs
        </a>
        <span className="text-xs text-muted-foreground/40">·</span>
        <a href="https://github.com/openviglet/turing" target="_blank" rel="noopener noreferrer" className="text-[11px] text-muted-foreground/50 hover:text-muted-foreground transition-colors">
          GitHub
        </a>
        <span className="text-xs text-muted-foreground/40">·</span>
        <a href="https://www.linkedin.com/company/viglet.com" target="_blank" rel="noopener noreferrer" className="text-[11px] text-muted-foreground/50 hover:text-muted-foreground transition-colors">
          LinkedIn
        </a>
      </div>
    </footer>
  )
}
