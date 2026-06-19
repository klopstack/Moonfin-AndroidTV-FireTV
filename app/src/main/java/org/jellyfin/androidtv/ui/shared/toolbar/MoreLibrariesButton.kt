package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.AggregatedLibrary
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.StonecrusherTheme
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonColors
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

/**
 * Expandable "More" button that shows additional libraries beyond the primary types.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoreLibrariesButton(
	activeLibraryId: UUID?,
	userViews: List<BaseItemDto>,
	aggregatedLibraries: List<AggregatedLibrary>,
	enableMultiServer: Boolean,
	colors: ButtonColors,
	activeColors: ButtonColors,
	navigationRepository: NavigationRepository,
	itemLauncher: ItemLauncher,
) {
	val additionalUserViews = partitionUserViews(userViews).second
	val additionalAggregated = partitionAggregatedLibraries(aggregatedLibraries).second
	val hasAdditional = if (enableMultiServer && aggregatedLibraries.isNotEmpty()) {
		additionalAggregated.isNotEmpty()
	} else {
		additionalUserViews.isNotEmpty()
	}
	if (!hasAdditional) return

	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()
	val scope = rememberCoroutineScope()
	val bringIntoViewRequester = remember { BringIntoViewRequester() }

	var isExpanded by remember { mutableStateOf(false) }
	val hasActiveLibrary = if (enableMultiServer && aggregatedLibraries.isNotEmpty()) {
		additionalAggregated.any { activeLibraryId == it.library.id }
	} else {
		additionalUserViews.any { activeLibraryId == it.id }
	}

	LaunchedEffect(isFocused) {
		if (isFocused) {
			scope.launch {
				bringIntoViewRequester.bringIntoView()
			}
		}
	}

	val contentPadding = if (isFocused) {
		PaddingValues(horizontal = 16.dp, vertical = 10.dp)
	} else {
		PaddingValues(horizontal = 5.dp, vertical = 10.dp)
	}

	Box(
		modifier = Modifier.onFocusChanged { focusState ->
			if (!focusState.hasFocus) isExpanded = false
		}
	) {
		Row(
			modifier = Modifier.focusGroup(),
			horizontalArrangement = Arrangement.spacedBy(0.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Button(
				onClick = { isExpanded = !isExpanded },
				colors = if (hasActiveLibrary) activeColors else colors,
				contentPadding = contentPadding,
				modifier = Modifier
					.then(if (!isFocused) Modifier.requiredWidthIn(max = 36.dp) else Modifier)
					.bringIntoViewRequester(bringIntoViewRequester),
				interactionSource = interactionSource,
			) {
				Row(
					horizontalArrangement = Arrangement.Center,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_more),
						contentDescription = stringResource(R.string.lbl_more),
					)

					if (isFocused) {
						Spacer(modifier = Modifier.width(8.dp))
						ProvideTextStyle(
							StonecrusherTheme.typography.default.copy(fontWeight = FontWeight.Bold)
						) {
							Text(
								text = stringResource(R.string.lbl_more),
								modifier = Modifier.padding(end = 4.dp)
							)
						}
					}
				}
			}

			AnimatedVisibility(
				visible = isExpanded,
				enter = expandHorizontally(
					expandFrom = Alignment.Start,
					animationSpec = tween(durationMillis = 250)
				) + fadeIn(animationSpec = tween(durationMillis = 250)),
				exit = shrinkHorizontally(
					shrinkTowards = Alignment.Start,
					animationSpec = tween(durationMillis = 200)
				) + fadeOut(animationSpec = tween(durationMillis = 200)),
			) {
				Row(
					horizontalArrangement = Arrangement.spacedBy(4.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Spacer(modifier = Modifier.width(8.dp))

					ProvideTextStyle(
						StonecrusherTheme.typography.default.copy(fontWeight = FontWeight.Bold)
					) {
						if (enableMultiServer && aggregatedLibraries.isNotEmpty()) {
							additionalAggregated.forEach { aggLib ->
								val isActiveLibrary = activeLibraryId == aggLib.library.id

								Button(
									onClick = {
										if (!isActiveLibrary) {
											navigateToLibrary(
												library = aggLib.library,
												navigationRepository = navigationRepository,
												itemLauncher = itemLauncher,
												serverId = aggLib.server.id,
												userId = aggLib.userId,
											)
										}
									},
									colors = if (isActiveLibrary) activeColors else colors,
								) {
									Text(aggLib.displayName)
								}
							}
						} else {
							additionalUserViews.forEach { library ->
								val isActiveLibrary = activeLibraryId == library.id

								Button(
									onClick = {
										if (!isActiveLibrary) {
											navigateToLibrary(
												library = library,
												navigationRepository = navigationRepository,
												itemLauncher = itemLauncher,
											)
										}
									},
									colors = if (isActiveLibrary) activeColors else colors,
								) {
									Text(library.name ?: "")
								}
							}
						}
					}

					Spacer(modifier = Modifier.width(4.dp))
				}
			}
		}
	}
}
