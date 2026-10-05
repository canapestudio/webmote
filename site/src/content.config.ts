import { defineCollection } from 'astro:content';
import type { Loader } from 'astro/loaders';
import { z } from 'astro/zod';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { frenchSpacing } from './i18n/typography';

/**
 * The privacy policy is published from its single source, docs/privacy-policy.md, so the
 * page Google Play links to can never drift from the policy in the repository.
 *
 * That file holds the English policy, a `---` rule, then the French policy. Each half starts with
 * an `# H1` title and an italic line with the effective date; the site renders those itself.
 */
const POLICY = '../docs/privacy-policy.md';

function splitPolicy(source: string) {
  const halves = source.split(/^---\s*$/m);
  if (halves.length !== 2) {
    throw new Error(`${POLICY}: expected the English and French policies separated by one "---" line.`);
  }
  return halves.map((half, i) => {
    const lines = half.trim().split('\n');
    const title = lines[0]?.match(/^# (.+)$/)?.[1];
    const effective = lines.slice(1).find((line) => line.trim() !== '');
    if (!title || !effective || !/^_.+_$/.test(effective.trim())) {
      throw new Error(`${POLICY}: policy ${i + 1} must start with "# Title" and an "_Effective …_" line.`);
    }
    const effectiveText = effective
      .trim()
      .slice(1, -1)
      // Drop the in-document link to the other language: the site has its own language switch.
      .replace(/\s*·\s*\[[^\]]*\]\([^)]*\)/, '');
    const body = lines.slice(lines.indexOf(effective) + 1).join('\n').trim();
    return { title, effective: effectiveText, body };
  });
}

function privacyPolicyLoader(): Loader {
  return {
    name: 'privacy-policy',
    async load({ config, store, renderMarkdown, watcher, logger }) {
      const fileURL = new URL(POLICY, config.root);
      const path = fileURLToPath(fileURL);

      const sync = async () => {
        const [enPolicy, frPolicy] = splitPolicy(await readFile(fileURL, 'utf8'));
        store.clear();
        for (const [lang, policy] of [
          ['en', enPolicy],
          ['fr', { ...frPolicy, title: frenchSpacing(frPolicy.title), body: frenchSpacing(frPolicy.body) }],
        ] as const) {
          store.set({
            id: lang,
            data: { lang, title: policy.title, effective: policy.effective },
            body: policy.body,
            rendered: await renderMarkdown(policy.body, { fileURL }),
          });
        }
        logger.info(`Loaded the privacy policy from ${POLICY}`);
      };

      watcher?.add(path);
      watcher?.on('change', async (changed) => {
        if (changed === path) await sync();
      });
      await sync();
    },
  };
}

const privacy = defineCollection({
  loader: privacyPolicyLoader(),
  schema: z.object({
    lang: z.enum(['en', 'fr']),
    title: z.string(),
    effective: z.string(),
  }),
});

export const collections = { privacy };
