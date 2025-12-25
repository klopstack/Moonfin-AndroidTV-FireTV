package org.jellyfin.androidtv.ui.jellyseerr

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.leanback.widget.Presenter
import coil3.load
import coil3.request.crossfade
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrNetworkDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrStudioDto

/**
 * Presenter for network and studio cards with logo display
 * Uses pre-filtered duotone URLs from Seerr for consistent white appearance
 */
class NetworkStudioCardPresenter : Presenter() {
	private val CARD_WIDTH = 260
	private val CARD_HEIGHT = 130

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val cardView = LayoutInflater.from(parent.context)
			.inflate(R.layout.jellyseerr_network_studio_card, parent, false) as CardView

		cardView.isFocusable = true
		cardView.isFocusableInTouchMode = true

		return ViewHolder(cardView)
	}

	override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
		val cardView = viewHolder.view as CardView

		val logoView = cardView.findViewById<ImageView>(R.id.logo_image)

		val layoutParams = cardView.layoutParams
		layoutParams.width = CARD_WIDTH
		layoutParams.height = CARD_HEIGHT
		cardView.layoutParams = layoutParams

		when (item) {
			is JellyseerrNetworkDto -> {
				loadLogo(logoView, item.logoPath)
			}
			is JellyseerrStudioDto -> {
				loadLogo(logoView, item.logoPath)
			}
			else -> {
				logoView.setImageDrawable(null)
			}
		}
	}

	private fun loadLogo(logoView: ImageView, logoUrl: String?) {
		if (!logoUrl.isNullOrEmpty()) {
			logoView.load(logoUrl) {
				crossfade(true)
			}
		} else {
			logoView.setImageDrawable(null)
		}
	}

	override fun onUnbindViewHolder(viewHolder: ViewHolder) {
		val cardView = viewHolder.view as CardView
		val logoView = cardView.findViewById<ImageView>(R.id.logo_image)
		logoView.setImageDrawable(null)
	}
}
