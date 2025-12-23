package org.jellyfin.androidtv.ui.jellyseerr

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.leanback.widget.Presenter
import coil3.load
import coil3.request.crossfade
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrGenreDto
import kotlin.random.Random

class GenreCardPresenter : Presenter() {
	private val CARD_WIDTH = 260
	private val CARD_HEIGHT = 130
	private val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w780"

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val cardView = LayoutInflater.from(parent.context)
			.inflate(R.layout.jellyseerr_genre_card, parent, false) as CardView

		cardView.isFocusable = true
		cardView.isFocusableInTouchMode = true

		return ViewHolder(cardView)
	}

	override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
		val genre = item as? JellyseerrGenreDto ?: return
		val cardView = viewHolder.view as CardView
		val context = cardView.context

		val imageView = cardView.findViewById<ImageView>(R.id.genre_image)
		val titleView = cardView.findViewById<TextView>(R.id.genre_title)
		val overlay = cardView.findViewById<ViewGroup>(R.id.genre_overlay)

		titleView.text = genre.name

		// Set card size
		val layoutParams = cardView.layoutParams
		layoutParams.width = CARD_WIDTH
		layoutParams.height = CARD_HEIGHT
		cardView.layoutParams = layoutParams

		// Load backdrop image if available
		if (genre.backdrops.isNotEmpty()) {
			// Pick a random backdrop from the list for variety
			val randomBackdrop = genre.backdrops[Random.nextInt(genre.backdrops.size)]
			val backdropUrl = "$TMDB_IMAGE_BASE_URL$randomBackdrop"
			
			imageView.load(backdropUrl) {
				crossfade(true)
			}
		} else {
			imageView.setBackgroundColor(Color.parseColor("#1a1a1a"))
		}
	}

	override fun onUnbindViewHolder(viewHolder: ViewHolder) {
		val cardView = viewHolder.view as CardView
		val imageView = cardView.findViewById<ImageView>(R.id.genre_image)
		imageView.setImageDrawable(null)
	}
}
