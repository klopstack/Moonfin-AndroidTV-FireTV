package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.ui.composable.getResolutionName
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.extensions.ticks
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Lightweight View-based info row that displays metadata without Compose overhead.
 * Updates are simple property assignments with no recomposition.
 */
class SimpleInfoRowView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : LinearLayout(context, attrs), KoinComponent {
	
	private val userPreferences by inject<UserPreferences>()
	private val items = mutableListOf<TextView>()
	
	init {
		orientation = HORIZONTAL
		gravity = Gravity.CENTER_VERTICAL
		
		// Pre-create a pool of TextViews for reuse (increased for more metadata)
		repeat(12) {
			val textView = TextView(context).apply {
				setTextColor(ContextCompat.getColor(context, android.R.color.white))
				setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
				typeface = Typeface.DEFAULT
				setShadowLayer(3f, 0f, 1f, ContextCompat.getColor(context, android.R.color.black))
				setPadding(0, 0, dpToPx(12), 0)
			}
			items.add(textView)
			addView(textView)
		}
	}
	
	fun setItem(item: BaseItemDto?) {
		// Hide all items first
		items.forEach { 
			it.visibility = GONE
			it.text = ""
			it.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
		}
		
		if (item == null) return
		
		var index = 0
		
		// Rating (if not hidden)
		val ratingType = userPreferences[UserPreferences.defaultRatingType]
		if (ratingType != RatingType.RATING_HIDDEN) {
			item.communityRating?.let { rating ->
				setItemText(index++, "⭐ ${String.format("%.1f", rating)}")
			}
			item.criticRating?.let { rating ->
				val iconRes = if (rating >= 60f) R.drawable.ic_rt_fresh else R.drawable.ic_rt_rotten
				setItemTextWithIcon(index++, "${rating.toInt()}%", iconRes)
			}
		}
		
		// Date based on item type
		val dateText = when (item.type) {
			BaseItemKind.SERIES -> item.productionYear?.toString()
			BaseItemKind.EPISODE -> item.premiereDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
			else -> item.productionYear?.toString() 
				?: item.premiereDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
		}
		dateText?.let { setItemText(index++, it) }
		
		// Episode info (Season/Episode number)
		if (item.type == BaseItemKind.EPISODE) {
			val seasonNum = item.parentIndexNumber
			val episodeNum = item.indexNumber
			if (seasonNum != null && episodeNum != null) {
				setItemText(index++, "S${seasonNum}E${episodeNum}")
			}
		}
		
		// Official Rating (e.g., PG-13)
		item.officialRating?.let { rating ->
			if (rating.isNotBlank()) {
				setItemText(index++, rating)
			}
		}
		
		// Get media streams for detailed info
		val mediaSource = item.mediaSources?.firstOrNull()
		val videoStream = mediaSource?.mediaStreams?.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO }
		
		// Subtitle indicators
		val hasSdhSubtitles = mediaSource?.mediaStreams?.any { 
			it.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE && it.isHearingImpaired 
		} == true
		val hasCcSubtitles = mediaSource?.mediaStreams?.any { 
			it.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE && !it.isHearingImpaired 
		} == true
		
		if (hasSdhSubtitles) {
			setItemText(index++, "SDH")
		}
		if (hasCcSubtitles) {
			setItemText(index++, "CC")
		}
		
		// Video resolution
		if (videoStream?.width != null && videoStream.height != null) {
			val resolution = getResolutionName(
				context = context,
				width = videoStream.width!!,
				height = videoStream.height!!,
				interlaced = videoStream.isInterlaced
			)
			setItemText(index++, resolution)
		}
	}
	
	private fun setItemText(index: Int, text: String) {
		if (index < items.size) {
			items[index].apply {
				this.text = text
				visibility = VISIBLE
			}
		}
	}
	
	private fun setItemTextWithIcon(index: Int, text: String, iconRes: Int) {
		if (index < items.size) {
			items[index].apply {
				this.text = text
				val drawable = ContextCompat.getDrawable(context, iconRes)
				drawable?.setBounds(0, 0, dpToPx(16), dpToPx(16))
				setCompoundDrawables(drawable, null, null, null)
				compoundDrawablePadding = dpToPx(4)
				visibility = VISIBLE
			}
		}
	}
	
	private fun dpToPx(dp: Int): Int {
		return (dp * context.resources.displayMetrics.density).toInt()
	}
}
