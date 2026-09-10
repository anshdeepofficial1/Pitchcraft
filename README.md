<div align="center">

# ✨ Pitchcraft

**AI-powered website proposal generation for agencies, freelancers, and client-facing teams.**

[![Live App](https://img.shields.io/badge/Live-pitchcraftpro.vercel.app-000000?style=for-the-badge&logo=vercel&logoColor=white)](https://pitchcraftpro.vercel.app/)
![React](https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react&logoColor=000)
![TanStack](https://img.shields.io/badge/TanStack-Start-FF4154?style=for-the-badge)
![Supabase](https://img.shields.io/badge/Supabase-Postgres-3FCF8E?style=for-the-badge&logo=supabase&logoColor=white)

<a href="https://github.com/sponsors/anshdeepofficial"><img src="https://img.shields.io/badge/Sponsor-%E2%9D%A4-EA4AAA?style=for-the-badge&logo=githubsponsors&logoColor=white" alt="Sponsor on GitHub" /></a>
<a href="https://buymeacoffee.com/anshdeepofficial"><img src="https://img.shields.io/badge/Buy%20Me%20a%20Coffee-Support-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=000" alt="Buy Me a Coffee" /></a>

</div>

---

## ✨ Overview

Pitchcraft helps turn a guided project brief into a structured, client-ready website proposal. It is designed to make proposal creation faster and more consistent while keeping the final result clear enough to share directly with clients.

## 🚀 Highlights

- Guided proposal creation flow
- AI-assisted proposal generation
- Client-ready structured output
- Shareable proposal links
- Secure data access with Supabase RLS
- Export-oriented proposal workflow
- Modern responsive interface
- Production deployment support

## 🎯 Built For

- Web agencies
- Freelancers
- Sales and proposal teams
- Client-facing web development workflows

## 🛠️ Tech Stack

| Area | Technology |
| --- | --- |
| UI | React 19, Tailwind CSS v4, shadcn/ui |
| Full-stack framework | TanStack Start + Vite 8 |
| Database | Supabase Postgres + RLS |
| AI | OpenAI-compatible generation endpoint |
| Deployment | Nitro presets / Vercel support |

## ⚡ Local Setup

```bash
git clone https://github.com/anshdeepofficial/Pitchcraft.git
cd Pitchcraft
bun install
cp .env.example .env
bun run dev
```

`npm` can be used instead of Bun where preferred.

## 🔐 Environment & Security

Use `.env.example` as the configuration template. Keep server-only credentials such as `SUPABASE_SERVICE_ROLE_KEY` and `AI_API_KEY` out of client-exposed `VITE_` variables and never commit a real `.env` file.

## 🚀 Deployment

The repository supports Nitro deployment presets and includes Vercel configuration. Set the required environment variables on the deployment platform before building the production app.

## 🤝 Contributing

Keep changes focused on proposal quality, UX, reliability, secure sharing, and maintainable full-stack architecture. Test generation and shared proposal flows before opening a pull request.

---

<div align="center">
Built by <a href="https://github.com/anshdeepofficial">Anshdeep Singh</a> · Aniweb Designs
</div>
