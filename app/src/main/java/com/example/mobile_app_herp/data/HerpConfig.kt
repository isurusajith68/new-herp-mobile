package com.example.mobile_app_herp.data

import com.example.mobile_app_herp.BuildConfig

/**
 * Host layout of the new-herp backend. Tenant hosts are `{slug}-{module}.{base}`
 * (single label, so one wildcard cert covers every tenant), so the auth origin
 * and the API origin are both derived from the workspace slug the user types on
 * the login screen.
 */
object HerpConfig {
    /**
     * `hotel-erp.ceyinfo.com`, set in app/build.gradle.kts so a build can be
     * pointed elsewhere with -PdomainBase=… without editing source.
     *
     * Dev and prod share this domain and are separated by workspace, so the
     * address alone does not tell you which data you are looking at — the
     * workspace slug does.
     */
    val DOMAIN_BASE: String = BuildConfig.DOMAIN_BASE

    /**
     * A release build rather than a debug one.
     *
     * Comes from the build type, NOT from matching [DOMAIN_BASE] against a
     * hostname: both environments answer on the same domain, so any such
     * comparison would be meaningless, and the hostname would be duplicated here
     * and in build.gradle.kts where changing one silently makes the other lie.
     */
    val isProduction: Boolean = BuildConfig.IS_PRODUCTION

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
