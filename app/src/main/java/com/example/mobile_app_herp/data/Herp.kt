package com.example.mobile_app_herp.data

import android.content.Context

/**
 * Process-level holder for the session and client. Survives configuration
 * changes (rotation) without pulling in a ViewModel dependency, and keeps the
 * cookie jar a single instance — two jars over one refresh cookie would race
 * the server's single-use rotation and get the family revoked.
 */
object Herp {
    private var _prefs: Prefs? = null
    private var _client: HerpClient? = null
    private var _updater: GithubUpdater? = null

    /** Cached workspace module titles — the same for every property. */
    var moduleTitles: List<ModuleInfo> = emptyList()

    /** The property the user tapped, held so rotation does not reset the screen. */
    var selectedProperty: Property? = null

    /**
     * Whether the SERVER can actually send pushes (it reports this when a token
     * registers). False on a deployment with no Firebase credentials, and the
     * app then skips asking for notification permission it could never use.
     */
    var pushEnabled: Boolean = false

    fun init(context: Context) {
        if (_prefs == null) {
            val app = context.applicationContext
            val prefs = Prefs(app)
            _prefs = prefs
            _client = HerpClient(prefs)
            _updater = GithubUpdater(app)
        }
    }

    val prefs: Prefs get() = requireNotNull(_prefs) { "Herp.init() not called" }
    val client: HerpClient get() = requireNotNull(_client) { "Herp.init() not called" }
    val updater: GithubUpdater get() = requireNotNull(_updater) { "Herp.init() not called" }

    fun reset() {
        moduleTitles = emptyList()
        selectedProperty = null
    }
}

/** `front-desk` → `Front Desk`, for keys the workspace module list didn't cover. */
fun prettifyModuleKey(key: String): String =
    key.split('-', '_', '.')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
