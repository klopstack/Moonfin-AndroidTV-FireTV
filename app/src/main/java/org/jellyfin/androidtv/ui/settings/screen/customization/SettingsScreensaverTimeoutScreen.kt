package org.jellyfin.androidtv.ui.settings.screen.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsScreensaverTimeoutScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	var screensaverTimeout by rememberPreference(userPreferences, UserPreferences.screensaverInAppTimeout)
	
	val timeoutOptions = listOf(
		30000L to "30 seconds",
		60000L to "1 minute",
		180000L to "2.5 minutes",
		300000L to "5 minutes",
		600000L to "10 minutes",
		900000L to "15 minutes",
		1800000L to "30 minutes"
	)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_screensaver).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_screensaver_inapp_timeout)) },
			)
		}

		timeoutOptions.forEach { (value, label) ->
			item {
				ListButton(
					headingContent = { Text(label) },
					trailingContent = { RadioButton(checked = screensaverTimeout == value) },
					onClick = {
						screensaverTimeout = value
						router.back()
					}
				)
			}
		}
	}
}
