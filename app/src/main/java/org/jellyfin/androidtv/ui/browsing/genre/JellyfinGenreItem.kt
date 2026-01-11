package org.jellyfin.androidtv.ui.browsing.genre

import java.util.UUID

/**
 * Data class representing a genre with its backdrop image for display
 */
data class JellyfinGenreItem(
	val id: UUID,
	val name: String,
	val backdropUrl: String? = null,
	val itemCount: Int = 0,
	val parentId: UUID? = null, // Library ID if filtering by library
)
