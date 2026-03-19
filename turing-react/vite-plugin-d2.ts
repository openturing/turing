import type { Plugin } from "vite"

export function d2DevPlugin(): Plugin {
  let d2Instance: unknown = null

  return {
    name: "d2-dev-render",
    apply: "serve",
    configureServer(server) {
      server.middlewares.use("/api/d2/render", async (req, res) => {
        if (req.method !== "POST") {
          res.statusCode = 405
          res.end("Method not allowed")
          return
        }

        let body = ""
        for await (const chunk of req) {
          body += chunk
        }

        try {
          const { code } = JSON.parse(body) as { code: string }
          if (!d2Instance) {
            const { D2 } = await import("@terrastruct/d2")
            d2Instance = new D2()
          }
          const d2 = d2Instance as import("@terrastruct/d2").D2
          const result = await d2.compile(code, { layout: "dagre" } as never)
          const svg = await d2.render(result.diagram, {
            ...result.renderOptions,
            noXMLTag: true,
            pad: 20,
            themeID: 0,
            darkThemeID: 200,
          })

          res.setHeader("Content-Type", "application/json")
          res.end(JSON.stringify({ svg }))
        } catch (err) {
          res.statusCode = 400
          res.setHeader("Content-Type", "application/json")
          res.end(JSON.stringify({ error: String(err) }))
        }
      })
    },
  }
}
