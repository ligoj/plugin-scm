/*
 * Plugin "scm" — Source (service-level).
 *
 * Parent of the scm-<tool> plugins. It owns no view of its own — the
 * legacy `scm.js` was an empty `define({})` — so it ships only:
 *   - generic source i18n;
 *   - the parent→child delegation hooks that merge a tool plugin's row
 *     features / detail chips into its own output, resolved via
 *     `subPluginIdFor`.
 *
 * Authored as source — compiled to `/main/scm/vue/index.js` by Vite.
 */
import { useI18nStore } from '@ligoj/host'
import enMessages from './i18n/en.js'
import frMessages from './i18n/fr.js'
import service from './service.js'

const features = {
  renderFeatures: service.renderFeatures,
  renderDetailsKey: service.renderDetailsKey,
  renderDetailsFeatures: service.renderDetailsFeatures,
}

export default {
  id: 'scm',
  label: 'Source',
  // No routes / component — screens come from the tool plugins and the
  // host's generic subscription rows.
  install() {
    const i18n = useI18nStore()
    i18n.merge(enMessages, 'en')
    i18n.merge(frMessages, 'fr')
  },
  feature(action, ...args) {
    const fn = features[action]
    if (!fn) throw new Error(`Plugin "scm" has no feature "${action}"`)
    return fn(...args)
  },
  service,
  meta: { icon: 'mdi-source-branch', color: 'teal-darken-2' },
}

export { service }
