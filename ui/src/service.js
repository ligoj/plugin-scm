/*
 * Service layer for plugin "scm" (Source, service-level).
 *
 * The legacy `service/scm/scm.js` was `define({})` (or a thin base
 * class): the parent owns no rendering of its own. In the Vue split the
 * tool plugins own their rendering and the parent delegates the
 * subscription-row hooks (`renderFeatures`, `renderDetailsKey`,
 * `renderDetailsFeatures`) to the scm-<tool> sub-plugin resolved from
 * the node id — the same pattern as `vm` → `vm-aws` and `bt` → `bt-jira`.
 *
 * Kept free of Vue SFC imports so it can be unit-tested without a DOM.
 */
import { pluginRegistry } from '@ligoj/host'

/**
 * Derive the sub-plugin id for a scm tool subscription. A scm node id
 * is `service:scm:<tool>[:<instance>]` — segment 3 is the tool, so
 * `service:scm:<tool>:1` → `scm-<tool>`. Returns null when there is no
 * tool segment to delegate to.
 */
export function subPluginIdFor(subscription) {
  const nodeId = subscription?.node?.id || ''
  const parts = nodeId.split(':').filter(Boolean)
  if (parts.length < 3) return null
  return `${parts[1]}-${parts[2]}`
}

/**
 * Calls `feature(action, subscription)` on the loaded scm-<tool>
 * sub-plugin and returns its VNodes (or an empty array). Degrades to
 * `[]` when nothing is registered, the plugin lacks the action, or the
 * call throws — a sub-plugin must never break the parent's rendering.
 */
export function delegateToToolPlugin(subscription, action) {
  const subId = subPluginIdFor(subscription)
  if (!subId) return []
  const plugin = pluginRegistry.get(subId)
  if (typeof plugin?.feature !== 'function') return []
  try {
    const result = plugin.feature(action, subscription)
    if (result == null) return []
    return Array.isArray(result) ? result : [result]
  } catch (err) {
    if (!new RegExp(`no feature ["']${action}["']`).test(err?.message || '')) {
      console.warn(`[plugin:scm] delegate to ${subId}.${action} threw`, err)
    }
    return []
  }
}

const service = {
  subPluginIdFor,
  delegateToToolPlugin,

  /** Subscription-row buttons — delegated wholesale to the scm-<tool>. */
  renderFeatures(subscription) {
    const out = delegateToToolPlugin(subscription, 'renderFeatures')
    return out.length ? out : []
  },

  /** Resource-key chips for the details column — delegated to the tool. */
  renderDetailsKey(subscription) {
    const out = delegateToToolPlugin(subscription, 'renderDetailsKey')
    return out.length ? out : null
  },

  /** Live detail chips — delegated to the tool. */
  renderDetailsFeatures(subscription) {
    const out = delegateToToolPlugin(subscription, 'renderDetailsFeatures')
    return out.length ? out : null
  },
}

export default service
