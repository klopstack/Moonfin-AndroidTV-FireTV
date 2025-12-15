package org.jellyfin.androidtv.ui.preference.custom

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.jellyfin.androidtv.R

object PinCodeDialog {
	enum class Mode {
		SET,      // Setting a new PIN
		VERIFY    // Verifying existing PIN
	}

	fun show(
		context: Context,
		mode: Mode,
		onComplete: (String?) -> Unit
	) {
		val container = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(50, 40, 50, 10)
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
		}

		val title = TextView(context).apply {
			text = when (mode) {
				Mode.SET -> context.getString(R.string.lbl_enter_new_pin)
				Mode.VERIFY -> context.getString(R.string.lbl_enter_pin)
			}
			textSize = 18f
			gravity = Gravity.CENTER
			setPadding(0, 0, 0, 20)
		}

		val pinInput = EditText(context).apply {
			inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
			hint = context.getString(R.string.lbl_pin_code_hint)
			gravity = Gravity.CENTER
			textSize = 20f
			setPadding(20, 20, 20, 20)
		}

		container.addView(title)
		container.addView(pinInput)

		if (mode == Mode.SET) {
			val confirmInput = EditText(context).apply {
				inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
				hint = context.getString(R.string.lbl_confirm_pin)
				gravity = Gravity.CENTER
				textSize = 20f
				setPadding(20, 20, 20, 20)
			}
			container.addView(confirmInput)

			AlertDialog.Builder(context)
				.setView(container)
				.setPositiveButton(android.R.string.ok) { _, _ ->
					val pin = pinInput.text.toString()
					val confirm = confirmInput.text.toString()
					
					when {
						pin.isEmpty() -> {
							android.widget.Toast.makeText(
								context,
								R.string.lbl_pin_code_empty,
								android.widget.Toast.LENGTH_SHORT
							).show()
							onComplete(null)
						}
						pin.length < 4 -> {
							android.widget.Toast.makeText(
								context,
								R.string.lbl_pin_code_too_short,
								android.widget.Toast.LENGTH_SHORT
							).show()
							onComplete(null)
						}
						pin != confirm -> {
							android.widget.Toast.makeText(
								context,
								R.string.lbl_pin_code_mismatch,
								android.widget.Toast.LENGTH_SHORT
							).show()
							onComplete(null)
						}
						else -> onComplete(pin)
					}
				}
				.setNegativeButton(android.R.string.cancel) { _, _ ->
					onComplete(null)
				}
				.show()
				.also {
					pinInput.requestFocus()
				}
		} else {
			AlertDialog.Builder(context)
				.setView(container)
				.setPositiveButton(android.R.string.ok) { _, _ ->
					val pin = pinInput.text.toString()
					if (pin.isEmpty()) {
						android.widget.Toast.makeText(
							context,
							R.string.lbl_pin_code_empty,
							android.widget.Toast.LENGTH_SHORT
						).show()
						onComplete(null)
					} else {
						onComplete(pin)
					}
				}
				.setNegativeButton(android.R.string.cancel) { _, _ ->
					onComplete(null)
				}
				.show()
				.also {
					pinInput.requestFocus()
				}
		}
	}
}
