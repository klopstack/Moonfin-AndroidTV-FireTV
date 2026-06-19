package org.jellyfin.androidtv.ui.startup

import android.app.AlertDialog
import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.databinding.DialogPinEntryBinding
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.util.PinCodeUtil

/**
 * TV-friendly PIN entry dialog with numeric keypad
 */
object PinEntryDialog {
	enum class Mode {
		SET,      // Setting a new PIN (with confirmation)
		VERIFY    // Verifying existing PIN
	}
	
	/**
	 * Show PIN entry dialog for setting or verifying a PIN
	 * @param context Android context
	 * @param mode Mode.SET to set a new PIN (with confirmation), Mode.VERIFY to verify existing PIN
	 * @param expectedPinLength Stored PIN length for verify auto-submit (0 = unknown)
	 * @param onComplete Callback with the entered PIN, or null if cancelled
	 * @param onForgotPin Optional callback for "Forgot PIN?" button (only shown in VERIFY mode)
	 */
	fun show(
		context: Context,
		mode: Mode,
		expectedPinLength: Int = 0,
		onComplete: (String?) -> Unit,
		onForgotPin: (() -> Unit)? = null,
	) {
		val autoSubmitEnabled = UserPreferences(context)[UserPreferences.pinAutoSubmitEnabled]
		when (mode) {
			Mode.SET -> showSetPinDialog(context, onComplete, autoSubmitEnabled)
			Mode.VERIFY -> showVerifyPinDialog(
				context = context,
				onComplete = onComplete,
				onForgotPin = onForgotPin,
				autoSubmitLength = resolveAutoSubmitLength(autoSubmitEnabled, expectedPinLength),
			)
		}
	}
	
	/**
	 * Simple verification dialog - just enter PIN once
	 */
	private fun showVerifyPinDialog(
		context: Context,
		onComplete: (String?) -> Unit,
		onForgotPin: (() -> Unit)? = null,
		autoSubmitLength: Int? = null,
	) {
		showPinDialog(
			context = context,
			title = context.getString(R.string.lbl_enter_pin),
			onPinEntered = { pin ->
				onComplete(pin)
			},
			onCancel = {
				onComplete(null)
			},
			onForgotPin = onForgotPin,
			autoSubmitLength = autoSubmitLength,
		)
	}
	
	/**
	 * Set PIN dialog - enter PIN twice for confirmation
	 */
	private fun showSetPinDialog(
		context: Context,
		onComplete: (String?) -> Unit,
		autoSubmitEnabled: Boolean,
	) {
		var firstPin: String? = null
		
		// First PIN entry - never auto-submit; the user may be entering a longer PIN
		showPinDialog(
			context = context,
			title = context.getString(R.string.lbl_enter_new_pin),
			autoSubmitLength = null,
			onPinEntered = { pin ->
				when {
					pin.isEmpty() -> {
						Toast.makeText(context, R.string.lbl_pin_code_empty, Toast.LENGTH_SHORT).show()
						onComplete(null)
					}
					pin.length < PinCodeUtil.MIN_PIN_LENGTH -> {
						Toast.makeText(context, R.string.lbl_pin_code_too_short, Toast.LENGTH_SHORT).show()
						onComplete(null)
					}
					else -> {
						firstPin = pin
						// Show confirmation dialog
						showPinDialog(
							context = context,
							title = context.getString(R.string.lbl_confirm_pin),
							autoSubmitLength = resolveAutoSubmitLength(autoSubmitEnabled, pin.length),
							onPinEntered = { confirmPin ->
								if (confirmPin == firstPin) {
									onComplete(confirmPin)
								} else {
									Toast.makeText(context, R.string.lbl_pin_code_mismatch, Toast.LENGTH_SHORT).show()
									onComplete(null)
								}
							},
							onCancel = {
								onComplete(null)
							}
						)
					}
				}
			},
			onCancel = {
				onComplete(null)
			}
		)
	}
	
	/**
	 * Low-level PIN dialog display
	 */
	private fun showPinDialog(
		context: Context,
		title: String? = null,
		onPinEntered: (String) -> Unit,
		onCancel: () -> Unit = {},
		onForgotPin: (() -> Unit)? = null,
		autoSubmitLength: Int? = null,
	) {
		val binding = DialogPinEntryBinding.inflate(LayoutInflater.from(context))
		
		// Set custom title if provided
		if (title != null) {
			binding.dialogTitle.text = title
		}
		
		// Prevent the system keyboard from appearing - multiple approaches for reliability
		binding.pinInput.showSoftInputOnFocus = false
		binding.pinInput.isFocusable = true
		binding.pinInput.isFocusableInTouchMode = false
		
		val dialog = AlertDialog.Builder(context)
			.setView(binding.root)
			.setCancelable(true)
			.create()
		
		// Hide keyboard when dialog window is created
		dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

		fun submitPin() {
			val pin = binding.pinInput.text.toString()
			if (pin.isNotEmpty()) {
				dialog.dismiss()
				onPinEntered(pin)
			}
		}

		fun appendPinDigit(digit: String) {
			val currentText = binding.pinInput.text?.toString() ?: ""
			if (currentText.length < PinCodeUtil.MAX_PIN_LENGTH) {
				binding.pinInput.append(digit)
				binding.pinError.isVisible = false
				if (autoSubmitLength != null && binding.pinInput.text?.length == autoSubmitLength) {
					submitPin()
				}
			}
		}
		
		// Setup numeric keypad buttons
		binding.pinButton0.setOnClickListener { appendPinDigit("0") }
		binding.pinButton1.setOnClickListener { appendPinDigit("1") }
		binding.pinButton2.setOnClickListener { appendPinDigit("2") }
		binding.pinButton3.setOnClickListener { appendPinDigit("3") }
		binding.pinButton4.setOnClickListener { appendPinDigit("4") }
		binding.pinButton5.setOnClickListener { appendPinDigit("5") }
		binding.pinButton6.setOnClickListener { appendPinDigit("6") }
		binding.pinButton7.setOnClickListener { appendPinDigit("7") }
		binding.pinButton8.setOnClickListener { appendPinDigit("8") }
		binding.pinButton9.setOnClickListener { appendPinDigit("9") }
		
		binding.pinButtonClear.setOnClickListener {
			val text = binding.pinInput.text
			if (text != null && text.isNotEmpty()) {
				text.delete(text.length - 1, text.length)
			}
			binding.pinError.isVisible = false
		}
		
		binding.pinButtonSubmit.setOnClickListener { submitPin() }
		
		// Setup forgot PIN button if callback provided
		if (onForgotPin != null) {
			binding.forgotPinButton.isVisible = true
			binding.forgotPinButton.setOnClickListener {
				dialog.dismiss()
				onForgotPin()
			}
		} else {
			binding.forgotPinButton.isVisible = false
		}
		
		// Submit on Enter/Done key
		binding.pinInput.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
				submitPin()
				true
			} else {
				false
			}
		}
		
		// Create a shared key listener for all PIN entry elements
		val pinKeyListener = View.OnKeyListener { view, keyCode, event ->
			if (event.action == KeyEvent.ACTION_DOWN) {
				when (keyCode) {
					KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
						// Let buttons handle their own click, only intercept for text field
						if (view == binding.pinInput) {
							submitPin()
							true
						} else {
							false // Let the button's click listener handle it
						}
					}
					// Handle numeric key presses
					KeyEvent.KEYCODE_0 -> {
						appendPinDigit("0")
						true
					}
					KeyEvent.KEYCODE_1 -> {
						appendPinDigit("1")
						true
					}
					KeyEvent.KEYCODE_2 -> {
						appendPinDigit("2")
						true
					}
					KeyEvent.KEYCODE_3 -> {
						appendPinDigit("3")
						true
					}
					KeyEvent.KEYCODE_4 -> {
						appendPinDigit("4")
						true
					}
					KeyEvent.KEYCODE_5 -> {
						appendPinDigit("5")
						true
					}
					KeyEvent.KEYCODE_6 -> {
						appendPinDigit("6")
						true
					}
					KeyEvent.KEYCODE_7 -> {
						appendPinDigit("7")
						true
					}
					KeyEvent.KEYCODE_8 -> {
						appendPinDigit("8")
						true
					}
					KeyEvent.KEYCODE_9 -> {
						appendPinDigit("9")
						true
					}
					KeyEvent.KEYCODE_DEL -> {
						// Delete key removes last digit
						val text = binding.pinInput.text
						if (text != null && text.isNotEmpty()) {
							text.delete(text.length - 1, text.length)
							binding.pinError.isVisible = false
						}
						true
					}
					KeyEvent.KEYCODE_BACK -> {
						// Back key deletes digit or closes dialog
						val text = binding.pinInput.text
						if (text != null && text.isNotEmpty()) {
							text.delete(text.length - 1, text.length)
							binding.pinError.isVisible = false
						} else {
							dialog.dismiss()
							onCancel()
						}
						true
					}
					else -> false
				}
			} else {
				false
			}
		}
		
		// Apply key listener to all PIN entry elements
		binding.pinInput.setOnKeyListener(pinKeyListener)
		binding.pinButton0.setOnKeyListener(pinKeyListener)
		binding.pinButton1.setOnKeyListener(pinKeyListener)
		binding.pinButton2.setOnKeyListener(pinKeyListener)
		binding.pinButton3.setOnKeyListener(pinKeyListener)
		binding.pinButton4.setOnKeyListener(pinKeyListener)
		binding.pinButton5.setOnKeyListener(pinKeyListener)
		binding.pinButton6.setOnKeyListener(pinKeyListener)
		binding.pinButton7.setOnKeyListener(pinKeyListener)
		binding.pinButton8.setOnKeyListener(pinKeyListener)
		binding.pinButton9.setOnKeyListener(pinKeyListener)
		binding.pinButtonClear.setOnKeyListener(pinKeyListener)
		binding.pinButtonSubmit.setOnKeyListener(pinKeyListener)
		binding.root.setOnKeyListener(pinKeyListener)
		
		dialog.setOnCancelListener {
			onCancel()
		}
		
		dialog.show()
		
		// Explicitly hide keyboard and focus on the first button instead of EditText
		val imm = context.getSystemService<InputMethodManager>()
		imm?.hideSoftInputFromWindow(binding.pinInput.windowToken, 0)
		
		// Focus on the first numeric button instead of the EditText to avoid keyboard
		binding.pinButton1.requestFocus()
	}
	
	fun showIncorrectPin(binding: DialogPinEntryBinding) {
		binding.pinError.isVisible = true
		binding.pinInput.text?.clear()
	}

	private fun resolveAutoSubmitLength(autoSubmitEnabled: Boolean, pinLength: Int): Int? =
		if (autoSubmitEnabled && pinLength in PinCodeUtil.MIN_PIN_LENGTH..PinCodeUtil.MAX_PIN_LENGTH) {
			pinLength
		} else {
			null
		}
}
