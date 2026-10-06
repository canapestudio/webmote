/**
 * Site-wide facts. Copy lives in src/i18n/; this file holds what isn't a matter of wording.
 */

/**
 * ┌──────────────────────────────────────────────────────────────────────────────┐
 * │ GOOGLE PLAY                                                                  │
 * │ While `live` is false, every Play call to action reads "Coming soon to       │
 * │ Google Play" and invites people to join the closed test by email.            │
 * │ When Webmote is published, set `live: true`: the official "Get it on Google  │
 * │ Play" badge then links to `url`, and the footer adds Google's attribution.   │
 * └──────────────────────────────────────────────────────────────────────────────┘
 */
export const GOOGLE_PLAY = {
  live: false,
  url: 'https://play.google.com/store/apps/details?id=app.canapestudio.webmote',
} as const;

/**
 * ┌──────────────────────────────────────────────────────────────────────────────┐
 * │ OPEN SOURCE                                                                  │
 * │ While `public` is false, the site says nothing about the source code. Once   │
 * │ the repository is public, set `public: true`: the footer then links the      │
 * │ source code and the Liberapay page, and says Webmote is free software.       │
 * │ Once Webmote is listed on F-Droid, set `fdroid.live: true` to show its link  │
 * │ next to the Google Play call to action.                                      │
 * └──────────────────────────────────────────────────────────────────────────────┘
 */
export const OPEN_SOURCE = {
  public: true,
  sourceUrl: 'https://github.com/canapestudio/webmote',
  liberapayUrl: 'https://liberapay.com/canapestudio/',
  fdroid: {
    live: false,
    url: 'https://f-droid.org/packages/app.canapestudio.webmote/',
  },
} as const;

export const CONTACT_EMAIL = 'hello@canapestudio.app';

/** Subject line of the "join the closed test" email, per language. */
export const TEST_EMAIL_SUBJECT = {
  en: 'Webmote closed test',
  fr: 'Test fermé de Webmote',
} as const;
