# Webmote website

The marketing site for Webmote, meant for **https://webmote.canapestudio.app**, in English (`/`) and
French (`/fr/`), with the privacy policy that Google Play links to at **`/privacy/`** (and
`/fr/privacy/`).

It's an [Astro](https://docs.astro.build) project that builds to plain static files: no server, no
client-side JavaScript, no cookies, no analytics, and no request to any other site. Fonts and images
are served from the site itself, and every page carries a Content-Security-Policy that makes the
browser refuse anything else.

## Commands

Node 22.12 or later. From this directory:

| Command | What it does |
|---|---|
| `npm ci` | Installs the exact dependencies from `package-lock.json` |
| `npm run dev` | Serves the site with live reload at http://localhost:4321 |
| `npm run build` | Builds the site into `dist/` |
| `npm run preview` | Serves `dist/` locally, to check the real build (the CSP only applies there) |
| `npm run check` | Type-checks the components and the copy |

Astro collects anonymous usage data from its command-line tool (not from the site's visitors). To
turn that off on your machine: `npx astro telemetry disable`.

## Where things are

```
site/
├── astro.config.mjs      site URL, i18n (en default, fr under /fr/), sitemap, CSP
├── public/               copied as is: robots.txt, favicons
└── src/
    ├── config.ts         Google Play switch, contact email, links to the studio's site
    ├── i18n/en.ts        all the English copy
    ├── i18n/fr.ts        all the French copy (TypeScript checks it has the same keys as en.ts)
    ├── content.config.ts loads the privacy policy from ../docs/privacy-policy.md
    ├── screens.ts        which screenshot goes with which language
    ├── assets/           screenshots, icon, feature graphics, Google Play badges
    ├── components/       HomePage, PrivacyPage, Header, Footer, Phone (the CSS phone frame)…
    │   └── home/         the home page's sections
    ├── layouts/Base.astro  <head>: meta, Open Graph, hreflang, icons, font preload
    ├── pages/            index, privacy, fr/index, fr/privacy, 404
    └── styles/global.css colours (light and dark), type scale, buttons
```

- **Copy** lives only in `src/i18n/en.ts` and `src/i18n/fr.ts`; the pages share one set of
  components. In French, write ordinary spaces before `: ; ? ! »`: the site turns them into the
  right non-breaking spaces. Don't claim "no ads" anywhere: it was deliberately left out of the
  listing.
- **Privacy policy**: edit `docs/privacy-policy.md`, the single source. The site splits it at
  its `---` line into the English and French pages and shows each half's `# Title` and
  `_Effective …_` line as the page header. A build fails with a clear message if that shape changes.
- **Screenshots** come from `fastlane/metadata/android/{en-US,fr-FR}/images/phoneScreenshots/`.
  After regenerating them (`tools/shot.sh`), copy them into `src/assets/screens/{en,fr}/`
  under the names used there. The build resizes them and serves AVIF and WebP at the sizes
  actually displayed.
- **Fonts**: Roboto (SIL Open Font License) from the `@fontsource-variable/roboto` package, bundled
  at build time.

## When Webmote is live on Google Play

Open `src/config.ts` and set `live: true` in `GOOGLE_PLAY`:

```ts
export const GOOGLE_PLAY = {
  live: true,
  url: 'https://play.google.com/store/apps/details?id=app.canapestudio.webmote',
} as const;
```

Then rebuild. That one change:

- replaces "Coming soon to Google Play" and the "Join the closed test" button with Google's official
  "Get it on Google Play" badge (English or French), linking to `url`;
- adds Google's required line to the footer: "Google Play and the Google Play logo are trademarks
  of Google LLC.";
- removes the "When can I get it?" question from the FAQ.

The badges in `src/assets/brand/google-play-badge-{en,fr}.png` are Google's official artwork, only
trimmed of their transparent margin. Google's rules: don't alter them, keep them at least 28 px tall
(the site shows them at 64 px) with clear space of a quarter of their height, and make them at least
as large as any other store's badge.

## When the source code is public, and on F-Droid

`OPEN_SOURCE` in `src/config.ts` works the same way:

- `public: true` adds "Source code" and "Donate" (Liberapay) to the footer links, and a line saying
  Webmote is free software under the GNU GPL v3.
- `fdroid.live: true` adds a "Get it on F-Droid" link after the Google Play call to action. It's a
  plain button, which keeps the Google Play badge the largest store badge on the page.

## Publishing

`npm ci && npm run build` produces `dist/`, a folder of static files: pages are directories
(`/privacy/` is `privacy/index.html`), `404.html` is at the root, and the sitemap is
`sitemap-index.xml`. The canonical URLs, sitemap and Open Graph tags point to
`https://webmote.canapestudio.app` (change `site` in `astro.config.mjs` if the address changes).

The site is served by a **Cloudflare Worker with static assets** (`wrangler.jsonc`), deployed by
**Workers Builds** on every push to `main` of the private GitHub repository. `public/_headers` sets
the security and caching headers, and `.node-version` pins Node for the build.

### First-time setup (Cloudflare dashboard, once)

1. **Workers & Pages** → **Create** → **Import a repository**. Connect GitHub and give Cloudflare
   access to the repository.
2. Settings:
   - **Project name:** `webmote-site` (must match `name` in `wrangler.jsonc`)
   - **Root directory:** `site`
   - **Build command:** `npm run build`
   - **Deploy command:** `npx wrangler deploy` (the default)
   - **Build variables:** `ASTRO_TELEMETRY_DISABLED` = `1`
3. **Save and Deploy.** The first deploy also attaches `webmote.canapestudio.app` (the `routes`
   entry in `wrangler.jsonc`): Cloudflare creates the DNS record and certificate, since the
   `canapestudio.app` zone is on the same account.

After that, every push to `main` rebuilds and redeploys the site. To check a build without
deploying: `npm run build && npx wrangler deploy --dry-run`; to serve `dist/` locally with
Cloudflare's runtime and headers: `npx wrangler dev`.
