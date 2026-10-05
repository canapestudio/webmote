import { getRelativeLocaleUrl } from 'astro:i18n';
import { en, type Dictionary } from './en';
import { fr } from './fr';
import { frenchSpacing } from './typography';

export const languages = ['en', 'fr'] as const;
export type Lang = (typeof languages)[number];

/** BCP 47 tags for <html lang> and hreflang. */
export const htmlLang: Record<Lang, string> = { en: 'en', fr: 'fr' };

/** The other language, for the language switch. */
export const otherLang = (lang: Lang): Lang => (lang === 'en' ? 'fr' : 'en');

function mapStrings<T>(value: T, fn: (s: string) => string): T {
  if (typeof value === 'string') return fn(value) as T;
  if (Array.isArray(value)) return value.map((v) => mapStrings(v, fn)) as T;
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, mapStrings(v, fn)])) as T;
  }
  return value;
}

const dictionaries: Record<Lang, Dictionary> = {
  en,
  fr: mapStrings(fr, frenchSpacing),
};

export function useTranslations(lang: Lang): Dictionary {
  return dictionaries[lang];
}

/** Pages that exist in both languages, by their path without the locale prefix. */
export type PageKey = '' | 'privacy';

/** "/" or "/fr/", "/privacy/" or "/fr/privacy/". */
export function pagePath(lang: Lang, page: PageKey): string {
  return getRelativeLocaleUrl(lang, page);
}
