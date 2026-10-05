// @ts-check
import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';

// https://docs.astro.build/en/reference/configuration-reference/
export default defineConfig({
  site: 'https://webmote.canapestudio.app',
  // Every page is a directory (/privacy/ → privacy/index.html), linked with its trailing slash.
  trailingSlash: 'always',
  // Plain static output: no adapter, no on-demand rendering.
  output: 'static',

  i18n: {
    locales: ['en', 'fr'],
    defaultLocale: 'en',
    routing: { prefixDefaultLocale: false },
  },

  integrations: [
    sitemap({
      i18n: { defaultLocale: 'en', locales: { en: 'en', fr: 'fr' } },
    }),
  ],

  // The site loads nothing from anywhere else, and the browser enforces it: Astro writes a
  // Content-Security-Policy <meta> into every page, with hashes for its own styles.
  security: {
    csp: {
      directives: [
        "default-src 'self'",
        "img-src 'self' data:",
        "font-src 'self'",
        "connect-src 'self'",
        "base-uri 'self'",
        "form-action 'none'",
        "object-src 'none'",
      ],
    },
  },

  // The only Markdown is the privacy policy, which has no code: no syntax highlighter (Shiki's
  // inline styles would clash with the CSP).
  markdown: { syntaxHighlight: false },

  // The CSS is small (about 10 kB per page): inlining it saves a render-blocking request.
  build: { inlineStylesheets: 'always' },

  devToolbar: { enabled: false },
});
