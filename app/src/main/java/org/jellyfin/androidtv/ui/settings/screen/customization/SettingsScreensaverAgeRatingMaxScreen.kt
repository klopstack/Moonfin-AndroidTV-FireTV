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
fun SettingsScreensaverAgeRatingMaxScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	var screensaverAgeRatingMax by rememberPreference(userPreferences, UserPreferences.screensaverAgeRatingMax)
	
	val ageRatingOptions = listOf(
		-1 to stringResource(R.string.pref_screensaver_ageratingmax_unlimited),
		0 to stringResource(R.string.pref_screensaver_ageratingmax_zero),
		6 to "6",
		12 to "12",
		13 to "13",
		16 to "16",
		17 to "17",
		18 to "18",
		21 to "21"
	)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_screensaver).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_screensaver_ageratingmax)) },
			)
		}

		ageRatingOptions.forEach { (value, label) ->
			item {
				ListButton(
					headingContent = { Text(label) },
					trailingContent = { RadioButton(checked = screensaverAgeRatingMax == value) },
					onClick = {
						screensaverAgeRatingMax = value
						router.back()
					}
				)
			}
		}
	}
}
