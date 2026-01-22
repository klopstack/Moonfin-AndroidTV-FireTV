package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonColors
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.base.button.IconButtonDefaults

/**
 * A dropdown menu that appears from the user icon in the toolbar
 * Contains buttons for Change User, Settings, Jellyseerr, and SyncPlay
 */
@Composable
fun UserMenu(
	isOpen: Boolean,
	onOpenChanged: (Boolean) -> Unit,
	onSettings: () -> Unit,
	onJellyseerr: (() -> Unit)? = null,
	onSyncPlay: (() -> Unit)? = null,
	onChangeUser: () -> Unit,
	jellyseerrEnabled: Boolean = false,
	syncPlayEnabled: Boolean = false,
	colors: ButtonColors = ButtonDefaults.colors(),
	backgroundColor: Color = Color.Gray,
	backgroundAlpha: Float = 1f,
) {
	if (isOpen) {
		Popup(
			properties = PopupProperties(
				focusable = true,
				dismissOnBackPress = true,
				dismissOnClickOutside = true
			),
			onDismissRequest = { onOpenChanged(false) }
		) {
			Column(
				modifier = Modifier
					.offset(x = (-2).dp, y = (-10).dp)
					.background(
						color = backgroundColor.copy(alpha = backgroundAlpha),
						shape = RoundedCornerShape(8.dp)
					)
					.padding(vertical = 8.dp)
					.focusGroup()
			) {
				// Change User button (first)
				UserMenuButton(
					icon = ImageVector.vectorResource(R.drawable.ic_user),
					label = "Change User",
					onClick = {
						onChangeUser()
						onOpenChanged(false)
					},
					colors = colors,
				)

				// Settings button (second)
				UserMenuButton(
					icon = ImageVector.vectorResource(R.drawable.ic_settings),
					label = "Settings",
					onClick = {
						onSettings()
						onOpenChanged(false)
					},
					colors = colors,
				)

				if (jellyseerrEnabled && onJellyseerr != null) {
					UserMenuButton(
						icon = ImageVector.vectorResource(R.drawable.ic_jellyseerr_jellyfish),
						label = "Jellyseerr",
						onClick = {
							onJellyseerr()
							onOpenChanged(false)
						},
						colors = colors,
					)
				}

				if (syncPlayEnabled && onSyncPlay != null) {
					UserMenuButton(
						icon = ImageVector.vectorResource(R.drawable.ic_syncplay),
						label = "SyncPlay",
						onClick = {
							onSyncPlay()
							onOpenChanged(false)
						},
						colors = colors,
					)
				}
			}
		}
	}
}

/**
 * A single button in the user menu with an icon and text label
 */
@Composable
private fun UserMenuButton(
	icon: ImageVector,
	label: String,
	onClick: () -> Unit,
	colors: ButtonColors,
	modifier: Modifier = Modifier,
) {
	Button(
		onClick = onClick,
		colors = colors,
		modifier = modifier,
		contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
	) {
		Row(
			horizontalArrangement = Arrangement.Start,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(
				imageVector = icon,
				contentDescription = null,
			)
			Spacer(modifier = Modifier.width(12.dp))
			ProvideTextStyle(
				JellyfinTheme.typography.default.copy(fontWeight = FontWeight.Normal)
			) {
				Text(text = label)
			}
		}
	}
}
