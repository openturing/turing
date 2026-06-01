/*
 * Adobe Edge Delivery Services (EDS) block — Turing AI chat (RAG).
 *
 * Demonstrates token-by-token streaming via `createChatController`. The SDK is
 * vendored into `scripts/turing-sdk.js`; adjust the import to a CDN URL if you
 * prefer (e.g. https://cdn.jsdelivr.net/npm/@viglet/turing-sdk).
 *
 * Authoring: a block named "turing-chat" with the API base URL and SN site:
 *
 *   | turing-chat                                     |
 *   | https://turing.example.com/api | my-site        |
 *
 * @since 2026.3.1
 */
import {
  createTuringClient,
  createChatController,
} from "../../scripts/turing-sdk.js";

export default function decorate(block) {
  const cells = block.querySelectorAll(":scope > div > div");
  const baseURL = cells[0]?.textContent.trim() || "http://localhost:2700/api";
  const site = cells[1]?.textContent.trim() || "sample";
  block.textContent = "";

  const client = createTuringClient({ baseURL });
  const chat = createChatController(client, { site, persist: true });

  const root = document.createElement("div");
  root.className = "turing-chat";
  root.innerHTML = `
    <div class="turing-chat__messages" aria-live="polite"></div>
    <form class="turing-chat__form">
      <input class="turing-chat__input" type="text" placeholder="Ask anything…" autocomplete="off" />
      <button class="turing-chat__send" type="submit">Send</button>
    </form>
  `;
  block.append(root);

  const messagesEl = root.querySelector(".turing-chat__messages");
  const form = root.querySelector(".turing-chat__form");
  const input = root.querySelector(".turing-chat__input");
  const sendBtn = root.querySelector(".turing-chat__send");

  chat.subscribe((state) => {
    // Hide the whole block if the site has no GenAI configured.
    root.hidden = state.enabled === false;

    messagesEl.innerHTML = "";
    for (const msg of state.messages) {
      const bubble = document.createElement("div");
      bubble.className = `turing-chat__bubble turing-chat__bubble--${msg.role}`;
      bubble.textContent = msg.content || (state.isStreaming ? "…" : "");
      messagesEl.append(bubble);

      // Render chat-flow suggestion chips, if any.
      if (msg.options?.length) {
        const chips = document.createElement("div");
        chips.className = "turing-chat__chips";
        for (const label of msg.options) {
          const chip = document.createElement("button");
          chip.type = "button";
          chip.className = "turing-chat__chip";
          chip.textContent = label;
          chip.addEventListener("click", () => chat.send(label));
          chips.append(chip);
        }
        messagesEl.append(chips);
      }
    }
    messagesEl.scrollTop = messagesEl.scrollHeight;

    sendBtn.disabled = state.isStreaming;
    input.disabled = state.isStreaming;
  });

  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const text = input.value.trim();
    if (!text) return;
    input.value = "";
    chat.send(text);
  });
}
