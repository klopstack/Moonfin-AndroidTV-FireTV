package org.jellyfin.androidtv.ui.startup

import android.content.Context
import androidx.appcompat.app.AlertDialog
import org.jellyfin.androidtv.R

object AccessScheduleDeniedDialog {
	fun show(
		context: Context,
		nextAccessMessage: String?,
		onDismiss: () -> Unit,
	) {
		val message = if (nextAccessMessage.isNullOrBlank()) {
			context.getString(R.string.access_schedule_denied_message)
		} else {
			context.getString(R.string.access_schedule_denied_message_with_time, nextAccessMessage)
		}

		AlertDialog.Builder(context)
			.setTitle(R.string.access_schedule_denied_title)
			.setMessage(message)
			.setPositiveButton(R.string.lbl_ok) { _, _ -> onDismiss() }
			.setCancelable(false)
			.show()
	}
}
