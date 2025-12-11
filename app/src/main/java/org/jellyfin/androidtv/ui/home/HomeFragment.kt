package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.home.mediabar.MediaBarSlideshowViewModel
import org.jellyfin.androidtv.ui.home.mediabar.MediaBarState
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.android.ext.android.inject

class HomeFragment : Fragment() {
	private val mediaBarViewModel by inject<MediaBarSlideshowViewModel>()
	private val userSettingPreferences by inject<UserSettingPreferences>()

	private var titleView: TextView? = null
	private var logoView: ComposeView? = null
	private var infoRowView: SimpleInfoRowView? = null
	private var summaryView: TextView? = null
	private var backgroundImage: ComposeView? = null
	private var rowsFragment: HomeRowsFragment? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val view = inflater.inflate(R.layout.fragment_home, container, false)

		// Get references to views
		titleView = view.findViewById(R.id.title)
		infoRowView = view.findViewById(R.id.infoRow)
		summaryView = view.findViewById(R.id.summary)
		
		// Setup logo with AnimatedContent for smooth transitions
		logoView = view.findViewById<ComposeView>(R.id.logo).apply {
			setContent {
				val state by mediaBarViewModel.state.collectAsState()
				val playbackState by mediaBarViewModel.playbackState.collectAsState()
				val isFocused by mediaBarViewModel.isFocused.collectAsState()
				
				val selectedPosition = rowsFragment?.selectedPositionFlow?.collectAsState(initial = -1)?.value ?: -1
				val isMediaBarEnabled = userSettingPreferences.activeHomesections.contains(org.jellyfin.androidtv.constant.HomeSectionType.MEDIA_BAR)
				val shouldShowMediaBar = isFocused || (selectedPosition == 0 && isMediaBarEnabled) || selectedPosition == -1
				
				val logoUrl = if (state is MediaBarState.Ready && shouldShowMediaBar) {
					(state as MediaBarState.Ready).items.getOrNull(playbackState.currentIndex)?.logoUrl
				} else null
				
				AnimatedContent(
					targetState = logoUrl,
					transitionSpec = {
						fadeIn() togetherWith fadeOut()
					},
					label = "logo_transition"
				) { url ->
					if (url != null) {
						AsyncImage(
							model = url,
							contentDescription = null,
							modifier = Modifier.fillMaxSize(),
							contentScale = ContentScale.Fit
						)
					}
				}
			}
		}
		
		// Setup background with AnimatedContent for smooth transitions
		backgroundImage = view.findViewById<ComposeView>(R.id.backgroundImage).apply {
			setContent {
				val state by mediaBarViewModel.state.collectAsState()
				val playbackState by mediaBarViewModel.playbackState.collectAsState()
				val isFocused by mediaBarViewModel.isFocused.collectAsState()
				
				val selectedPosition = rowsFragment?.selectedPositionFlow?.collectAsState(initial = -1)?.value ?: -1
				val isMediaBarEnabled = userSettingPreferences.activeHomesections.contains(org.jellyfin.androidtv.constant.HomeSectionType.MEDIA_BAR)
				val shouldShowMediaBar = isFocused || (selectedPosition == 0 && isMediaBarEnabled) || selectedPosition == -1
				
				val backdropUrl = if (state is MediaBarState.Ready && shouldShowMediaBar) {
					(state as MediaBarState.Ready).items.getOrNull(playbackState.currentIndex)?.backdropUrl
				} else null
				
				AnimatedContent(
					targetState = backdropUrl,
					transitionSpec = {
						fadeIn() togetherWith fadeOut()
					},
					label = "backdrop_transition"
				) { url ->
					if (url != null) {
						AsyncImage(
							model = url,
							contentDescription = null,
							modifier = Modifier.fillMaxSize(),
							contentScale = ContentScale.Crop
						)
					}
				}
			}
		}

		// Setup toolbar Compose
		val toolbarView = view.findViewById<ComposeView>(R.id.toolbar)
		toolbarView.setContent {
			MainToolbar(
				activeButton = MainToolbarActiveButton.Home
			)
		}

		return view
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// Observe selected item state from HomeRowsFragment
		rowsFragment = childFragmentManager.findFragmentById(R.id.rowsFragment) as? HomeRowsFragment

		rowsFragment?.selectedItemStateFlow
			?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			?.onEach { state ->
				// Update views directly - lightweight View updates (no Compose overhead)
				titleView?.text = state.title
				summaryView?.text = state.summary
				
				// Update info row with metadata - simple property assignment
				infoRowView?.setItem(state.baseItem)
			}
			?.launchIn(lifecycleScope)

		// Update logo visibility based on whether we're showing media bar or not
		rowsFragment?.selectedPositionFlow
			?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			?.onEach { position ->
				updateLogoVisibility()
			}
			?.launchIn(lifecycleScope)

		// Observe media bar state changes
		mediaBarViewModel.state
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { state ->
				updateLogoVisibility()
			}
			.launchIn(lifecycleScope)

		// Observe media bar focus state
		mediaBarViewModel.isFocused
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { isFocused ->
				updateLogoVisibility()
			}
			.launchIn(lifecycleScope)
	}

	private fun updateLogoVisibility() {
		val state = mediaBarViewModel.state.value
		val isFocused = mediaBarViewModel.isFocused.value
		val selectedPosition = rowsFragment?.selectedPositionFlow?.value ?: -1
		
		val isMediaBarEnabled = userSettingPreferences.activeHomesections.contains(org.jellyfin.androidtv.constant.HomeSectionType.MEDIA_BAR)
		val shouldShowMediaBar = isFocused || (selectedPosition == 0 && isMediaBarEnabled) || selectedPosition == -1
		
		if (state is MediaBarState.Ready && shouldShowMediaBar) {
			val playbackState = mediaBarViewModel.playbackState.value
			val currentItem = state.items.getOrNull(playbackState.currentIndex)
			val hasLogo = currentItem?.logoUrl != null
			
			// Show logo view when we have a logo, otherwise show title
			logoView?.isVisible = hasLogo
			titleView?.isVisible = !hasLogo
		} else {
			// Hide logo when on other rows
			logoView?.isVisible = false
			titleView?.isVisible = true
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		titleView = null
		logoView = null
		summaryView = null
		infoRowView = null
		backgroundImage = null
		rowsFragment = null
	}
}
