package org.jellyfin.androidtv.util

/**
 * Builds a Jellyfin Web Quick Connect URL that pre-fills the authorization code.
 *
 * @see [jellyfin-web quickConnect route](https://github.com/jellyfin/jellyfin-web/blob/master/src/apps/stable/routes/quickConnect/index.tsx)
 */
fun buildQuickConnectAuthorizeUrl(serverAddress: String, code: String): String {
	val base = serverAddress.trimEnd('/')
	val normalizedCode = code.replace("\\s".toRegex(), "")
	return "$base/web/index.html#!/quickconnect?code=$normalizedCode"
}
