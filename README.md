# Pitchcraft

Professional AI-powered website proposal generator by Aniweb Designs.

## What Pitchcraft Does

Pitchcraft helps agencies and freelancers create complete, client-ready website proposals in minutes.  
It turns a simple guided input form into a detailed proposal document you can share or export.

## Who It Is For

- Agencies preparing website proposals for clients
- Freelancers pitching web projects
- Teams that want faster, more consistent proposal quality

## Key Benefits

- Faster proposal creation with structured AI support
- Clear, professional output for client presentations
- Shareable proposals with secure access
- Export options for easy delivery and review

## Website URL

- Local app URL (default): pitchcraftpro.vercel.app
- Configurable public URL: set `VITE_APP_URL` in your environment

---

<details>
<summary><strong>Technical Details (Hidden for Non-Technical Readers)</strong></summary>

### Stack

- React 19 + TanStack Start (SSR) + Vite 8
- Tailwind CSS v4 + shadcn/ui
- Supabase (Postgres + RLS) for shared proposal links
- OpenAI-compatible AI endpoint for proposal generation

### Local Setup

```bash
bun install       # or npm install
cp .env.example .env
bun run dev       # http://localhost:8080
```

### Scripts

| Script | Purpose |
| --- | --- |
| `dev` | Start the dev server |
| `build` | Production build |
| `preview` | Preview the production build |
| `lint` | ESLint |
| `format` | Prettier |

### Environment Variables

Use `.env.example` as the template. Never commit a real `.env`.

Server-only secrets:

- `SUPABASE_SERVICE_ROLE_KEY`
- `AI_API_KEY`

Do not prefix server-only secrets with `VITE_`.

### Deployment

Set `NITRO_PRESET` for your host (`node-server`, `vercel`, `netlify`, `cloudflare-module`, etc.), then run:

```bash
bun run build
```

#### Vercel

`vercel.json` is included and uses:

- Install: `npm install`
- Build: `NITRO_PRESET=vercel npm run build`
- Output: `.vercel/output`

Recommended env vars:

`AI_API_BASE_URL`, `AI_API_KEY`, `AI_MODEL`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY`, optional `VITE_APP_URL`.

</details>

© Pitchcraft — Aniweb Designs — Animating Technologies

## Support Development

This project is free and open source. If you find it useful, you can support continued development through:

- [GitHub Sponsors](https://github.com/sponsors/anshdeepofficial)
- [Buy Me a Coffee](https://buymeacoffee.com/anshdeepofficial)
