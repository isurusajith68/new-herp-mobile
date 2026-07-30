package com.example.mobile_app_herp.data

/**
 * Host layout of the new-herp backend. Tenant hosts are `{slug}-{module}.{base}`
 * (single label, so one wildcard cert covers every tenant), so the auth origin
 * and the API origin are both derived from the workspace slug the user types on
 * the login screen.
 */
object HerpConfig {
    const val DOMAIN_BASE = "v3.ceyinfo.com"

    /**
     * The IdP only issues authorization codes to client/redirect pairs in its
     * static registry (apps/api/src/modules/idp/clients.ts), and that registry
     * has no mobile entry — so we present the launcher's identity. This is safe
     * here because the code never travels to the redirect target: POST /login
     * returns it in the JSON body and the app exchanges it directly. Nothing is
     * ever navigated to `redirectUri`; it only has to match what /token expects.
     *
     * Swap this for a real `herp-mobile` client once one is registered.
     */
    const val CLIENT_ID = "herp-launcher"

    fun authOrigin(slug: String) = "https://${slug}-auth.${DOMAIN_BASE}"

    fun apiOrigin(slug: String) = "https://${slug}-app.${DOMAIN_BASE}"

    fun redirectUri(slug: String) = "${apiOrigin(slug)}/callback"
}
