// Tell IndexNow (Bing, Yandex, Seznam, Naver) that every page in the live
// sitemap may have changed. Runs after the GitHub Pages deploy (see
// .github/workflows/publish-github-pages.yml). The same script ships
// docs.viglet.org.
//
// Google ignores IndexNow; Bing does not, and Bing feeds ChatGPT search,
// Copilot and DuckDuckGo. The key file public/<key>.txt proves we own the host,
// so the script first waits until the deploy actually serves it.
//
// Never fails the deploy: every problem is logged and the script exits 0.
//
// Usage: node scripts/indexnow.mjs <https://host> <key>

const [origin, key] = process.argv.slice(2);
if (!origin || !key) {
  console.warn("indexnow: usage: node scripts/indexnow.mjs <https://host> <key>");
  process.exit(0);
}

const host = new URL(origin).host;
const keyLocation = `${origin}/${key}.txt`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** GitHub Pages publishes a minute or two after the push; poll for the key. */
async function waitForKey(tries = 20, delayMs = 15_000) {
  for (let i = 0; i < tries; i++) {
    try {
      const res = await fetch(keyLocation, { cache: "no-store" });
      if (res.ok && (await res.text()).trim() === key) return true;
    } catch {
      // not up yet
    }
    await sleep(delayMs);
  }
  return false;
}

async function sitemapUrls() {
  const res = await fetch(`${origin}/sitemap.xml`, { cache: "no-store" });
  if (!res.ok) throw new Error(`sitemap.xml answered ${res.status}`);
  const xml = await res.text();
  return [...xml.matchAll(/<loc>\s*([^<\s]+)\s*<\/loc>/g)]
    .map((m) => m[1])
    .filter((u) => new URL(u).host === host);
}

try {
  if (!(await waitForKey())) {
    console.warn(`indexnow: ${keyLocation} never served the key — skipping.`);
    process.exit(0);
  }
  const urls = await sitemapUrls();
  // The protocol takes at most 10,000 URLs per request.
  for (let i = 0; i < urls.length; i += 10_000) {
    const urlList = urls.slice(i, i + 10_000);
    const res = await fetch("https://api.indexnow.org/indexnow", {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify({ host, key, keyLocation, urlList }),
    });
    console.log(`indexnow: ${urlList.length} URLs for ${host} -> ${res.status}`);
  }
} catch (err) {
  console.warn(`indexnow: ${err.message} — skipping.`);
}
