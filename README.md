<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/icon-dark.png" />
    <img src="docs/icon.png" alt="Poci" width="100" />
  </picture>
  <h1>Poci</h1>

A native Android **agent harness** — an on-device LLM runtime that runs tool-calling agents
across OpenAI-, Google-, and Anthropic-compatible providers 🤖🫖

[简体中文](README_ZH_CN.md) | [繁體中文](README_ZH_TW.md) | English
</div>

Poci runs a full agentic **turn** on-device, not just a single request: a tool-calling agent
loop with hooks, per-step context budgeting, subagents, and scheduled or background work — all
on top of a provider-agnostic wire layer. It began as a chat client and grew into a portable
harness for running agents wherever you carry your phone.

## ✨ Features

### 🤖 Agent runtime

- Tool-calling agent loop with hooks, wall-time / token budgets, and per-step context fit
- **Subagents** — dispatch specialized assistants, including detached **background** runs
- Scheduled & looping turns — recurring schedules plus in-session goal / loop commands
- Durable async work that survives process death (e.g. background-shell completions replayed at cold start)
- Per-assistant memory and optional **RAG** over a knowledge base, with history auto-compaction
- Fully customizable assistants: system prompts, params, tools, message transformers, prompt
  injections (mode / lorebook), regex, and event hooks

### 🛠️ Tools & integrations

- **MCP** (Model Context Protocol) client support
- Sandboxed **workspace**: file tools + background shell execution, with a write-capable file browser
- On-device **UI automation** (observe / tap / type), package-scoped with a kill switch
- Built-in tools: web search, web fetch, and image generation / editing
- **A2A** (agent-to-agent) server with LAN / mDNS discovery for delegating to the on-device agent
- **Skills** — bundled built-ins plus user-authored skills
- SillyTavern character-card import

### 🔄 Providers

- Any OpenAI-, Google-, or Anthropic-compatible endpoint — custom base URL / key / models
- Managed sign-in modes and Azure-style deployments
- Search backends: Exa, Tavily, Zhipu, LinkUp, Brave, Perplexity, SearXNG, and more
- QR-code export / import for provider configs

### 🎨 Chat & UX

- Material You design with dark mode
- Multimodal input (image, text, PDF, DOCX)
- Markdown rendering with code highlighting, LaTeX, tables, and Mermaid
- Message branching, AI translation, prompt variables
- Custom HTTP request headers and bodies
- Embedded web server for multi-platform access

> [!NOTE]
> The full shell / terminal and write-capable workspace surface ships in the **sideload** build;
> the **play** build restricts it. The flavor is a security boundary, not just store metadata.

## 🔨 Building

This project is developed using [Android Studio](https://developer.android.com/studio).

Technology stack:

- [Kotlin](https://kotlinlang.org/) (Development language)
- [Koin](https://insert-koin.io/) (Dependency Injection)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) (UI framework)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) (Preference data
  storage)
- [Room](https://developer.android.com/training/data-storage/room) (Database)
- [Coil](https://coil-kt.github.io/coil/) (Image loading)
- [Material You](https://m3.material.io/) (UI design)
- [Navigation Compose](https://developer.android.com/develop/ui/compose/navigation) (Navigation)
- [Okhttp](https://square.github.io/okhttp/) (HTTP client)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (JSON serialization)
- [compose-icons/lucide](https://composeicons.com/icon-libraries/lucide) (Icon library)

> [!TIP]
> You need a `google-services.json` file in the `app` folder to build the app. A placeholder file is
> sufficient for local builds that do not exercise Firebase.

See [CLAUDE.md](CLAUDE.md) / [AGENTS.md](AGENTS.md) for the build/test commands, module layout, and
architecture overview.

## 🙏 Credits

Poci is a fork of [**RikkaHub**](https://github.com/rikkahub/rikkahub) by the upstream RikkaHub authors —
the original project this is built on. All upstream copyright and license notices are preserved in
[LICENSE](LICENSE); the app icon and Poci branding in this fork are its own.

## 📄 License

This project is licensed under the **GNU AGPL v3.0** (with a segmented commercial-license clause for
commercial use or larger deployments) — see [LICENSE](LICENSE) for the full terms.

As a fork of an AGPL-3.0 project, the upstream license and copyright notices are preserved unchanged in
[LICENSE](LICENSE). If you redistribute or run this as a network service, you must comply with the AGPL
(including offering the corresponding source).
