package org.jellyfin.androidtv.ui.home.mediabar

import android.content.Context
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.*
private data class ItemWithApiClient(
    val item: BaseItemDto,
    val apiClient: ApiClient,
    val serverId: UUID? = null,
)
						ItemFields.OFFICIAL_RATING,
						ItemFields.CRITIC_RATING,
						ItemFields.COMMUNITY_RATING,
						ItemFields.OVERVIEW,
					)
				)
			).content
		} catch (e: Exception) {
			Timber.e(e, "MediaBar: Failed to fetch latest media")
			emptyList()
		}
	}

	fun loadSlideshowItems() {
		_state.value = MediaBarState.Loading
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val config = getConfig()
				val contentType = userSettingPreferences[UserSettingPreferences.mediaBarContentType]
				val loggedInServers = multiServerRepository.getLoggedInServers()
				val useMulti = loggedInServers.size > 1

				val rawItems: List<Triple<BaseItemDto, ApiClient, UUID?>> = if (useMulti) {
					val perServer = (config.maxItems / loggedInServers.size).coerceAtLeast(2)
					loggedInServers.map { session ->
						async {
							val list = when (contentType) {
								"movies" -> fetchItemsFromServer(session.apiClient, BaseItemKind.MOVIE, perServer)
								"tv" -> fetchItemsFromServer(session.apiClient, BaseItemKind.SERIES, perServer)
								else -> {
									val movies = async { fetchItemsFromServer(session.apiClient, BaseItemKind.MOVIE, perServer / 2 + 1) }
									val shows = async { fetchItemsFromServer(session.apiClient, BaseItemKind.SERIES, perServer / 2 + 1) }
									movies.await() + shows.await()
								}
							}
							list.map { Triple(it, session.apiClient, session.server.id) }
						}
					}.awaitAll().flatten()
				} else {
					val list = when (contentType) {
						"movies" -> fetchItemsFromServer(api, BaseItemKind.MOVIE, config.maxItems)
						"tv" -> fetchItemsFromServer(api, BaseItemKind.SERIES, config.maxItems)
						else -> {
							val movies = async { fetchItemsFromServer(api, BaseItemKind.MOVIE, config.maxItems / 2 + 1) }
							val shows = async { fetchItemsFromServer(api, BaseItemKind.SERIES, config.maxItems / 2 + 1) }
							movies.await() + shows.await()
						}
					}
					list.map { Triple(it, api, null) }
				}

				val filtered = parentalControlsRepository.filterItems(rawItems) { (item, _, _) -> item.officialRating }
					.filter { (item, _, _) -> item.backdropImageTags?.isNotEmpty() == true }
					.shuffled()
					.take(config.maxItems)

				items = filtered.map { (item, apiClient, serverId) ->
					MediaBarSlideItem(
						itemId = item.id,
						serverId = serverId,
						title = item.name.orEmpty(),
						overview = item.overview,
						backdropUrl = item.backdropImageTags?.firstOrNull()?.let { tag ->
							apiClient.imageApi.getItemImageUrl(
								itemId = item.id,
								imageType = ImageType.BACKDROP,
								tag = tag,
								maxWidth = 1920,
								quality = 90
							)
						},
						logoUrl = item.imageTags?.get(ImageType.LOGO)?.let { tag ->
							apiClient.imageApi.getItemImageUrl(
								itemId = item.id,
								imageType = ImageType.LOGO,
								tag = tag,
								maxWidth = 800,
							)
						},
						rating = item.officialRating,
						year = item.productionYear,
						genres = item.genres.orEmpty().take(3),
						runtime = item.runTimeTicks?.let { ticks -> (ticks / 10000) },
						criticRating = item.criticRating?.toInt(),
						communityRating = item.communityRating,
					)
				}

				withContext(Dispatchers.Main) {
					_state.value = if (items.isNotEmpty()) MediaBarState.Ready(items) else MediaBarState.Error("No items found")
					_playbackState.value = SlideshowPlaybackState(currentIndex = 0, isPaused = false, isTransitioning = false)
					resetAutoAdvanceTimer()
				}
			} catch (e: Exception) {
				Timber.e(e, "MediaBar: Failed to load slideshow items")
				withContext(Dispatchers.Main) {
					_state.value = MediaBarState.Error("Failed to load content")
				}
			}
		}
	}

	private fun refreshBackgroundItems() {
		if (items.isEmpty()) return

		viewModelScope.launch(Dispatchers.IO) {
			try {
				val currentIndex = _playbackState.value.currentIndex
				val contentType = userSettingPreferences[UserSettingPreferences.mediaBarContentType]

				val indicesToKeep = mutableSetOf<Int>().apply {
					add(currentIndex)
					add((currentIndex + 1) % items.size)
					add(if (currentIndex == 0) items.size - 1 else currentIndex - 1)
				}

				if (items.size <= 3) return@launch

				val itemsToReplace = items.size - indicesToKeep.size

				val newItemsWithApiClients: List<ItemWithApiClient> = run {
					val loggedInServers = multiServerRepository.getLoggedInServers()
					val useMultiServer = loggedInServers.size > 1
					if (useMultiServer) {
						val itemsPerServer = (itemsToReplace / loggedInServers.size).coerceAtLeast(2)
						loggedInServers.map { session ->
							async {
								try {
									val serverItems = when (contentType) {
										"movies" -> fetchItemsFromServer(session.apiClient, BaseItemKind.MOVIE, itemsPerServer)
										"tv" -> fetchItemsFromServer(session.apiClient, BaseItemKind.SERIES, itemsPerServer)
										else -> {
											val movies = async { fetchItemsFromServer(session.apiClient, BaseItemKind.MOVIE, itemsPerServer / 2 + 1) }
											val shows = async { fetchItemsFromServer(session.apiClient, BaseItemKind.SERIES, itemsPerServer / 2 + 1) }
											movies.await() + shows.await()
										}
									}
									serverItems.map { ItemWithApiClient(it, session.apiClient, session.server.id) }
								} catch (e: Exception) {
									Timber.e(e, "MediaBar refresh: Failed to fetch from server ${session.server.name}")
									emptyList()
								}
							}
						}.awaitAll().flatten()
					} else {
						val serverItems = when (contentType) {
							"movies" -> fetchItemsFromServer(api, BaseItemKind.MOVIE, itemsToReplace)
							"tv" -> fetchItemsFromServer(api, BaseItemKind.SERIES, itemsToReplace)
							else -> {
								val movies = async { fetchItemsFromServer(api, BaseItemKind.MOVIE, itemsToReplace / 2 + 1) }
								val shows = async { fetchItemsFromServer(api, BaseItemKind.SERIES, itemsToReplace / 2 + 1) }
								movies.await() + shows.await()
							}
						}
						serverItems.map { ItemWithApiClient(it, api) }
					}
				}.filter { it.item.backdropImageTags?.isNotEmpty() == true }
					.shuffled()
					.take(itemsToReplace)

				val newSlideItems = newItemsWithApiClients.map { (item, itemApiClient, serverId) ->
					MediaBarSlideItem(
						itemId = item.id,
						serverId = serverId,
						title = item.name.orEmpty(),
						overview = item.overview,
						backdropUrl = item.backdropImageTags?.firstOrNull()?.let { tag ->
							itemApiClient.imageApi.getItemImageUrl(
								itemId = item.id,
								imageType = ImageType.BACKDROP,
								tag = tag,
								maxWidth = 1920,
								quality = 90
							)
						},
						logoUrl = item.imageTags?.get(ImageType.LOGO)?.let { tag ->
							itemApiClient.imageApi.getItemImageUrl(
								itemId = item.id,
								imageType = ImageType.LOGO,
								tag = tag,
								maxWidth = 800,
							)
						},
						rating = item.officialRating,
						year = item.productionYear,
						genres = item.genres.orEmpty().take(3),
						runtime = item.runTimeTicks?.let { ticks -> (ticks / 10000) },
						criticRating = item.criticRating?.toInt(),
						communityRating = item.communityRating,
					)
				}

				val updatedItems = items.toMutableList()
				var newItemIndex = 0
				for (i in items.indices) {
					if (!indicesToKeep.contains(i) && newItemIndex < newSlideItems.size) {
						updatedItems[i] = newSlideItems[newItemIndex]
						newItemIndex++
					}
				}

				items = updatedItems
				_state.value = MediaBarState.Ready(items)

				withContext(Dispatchers.IO) {
					updatedItems.forEachIndexed { index, item ->
						if (!indicesToKeep.contains(index)) {
							item.backdropUrl?.let { url ->
								runCatching {
									val request = ImageRequest.Builder(context).data(url).build()
									imageLoader.enqueue(request)
								}
							}
							item.logoUrl?.let { url ->
								runCatching {
									val request = ImageRequest.Builder(context).data(url).build()
									imageLoader.enqueue(request)
								}
							}
						}
					}
				}
			} catch (e: Exception) {
				Timber.e(e, "MediaBar: Failed to refresh background items")
			}
		}
	}
}

private data class ItemWithApiClient(
    val item: BaseItemDto,
    val apiClient: ApiClient,
    val serverId: UUID? = null,
)
											item.logoUrl?.let { url ->
												try {
													val request = ImageRequest.Builder(context)
														.data(url)
														.build()
													imageLoader.enqueue(request)
												} catch (e: Exception) {
													Timber.d("Failed to preload logo for refreshed item ${item.title}: ${e.message}")
												}
											}
										}
									}
								}
							} catch (e: Exception) {
								Timber.e(e, "MediaBar: Failed to refresh background items")
							}
						}
					}
	 * - Manual refresh requested
	 */
	fun reloadContent() {
		// Cancel auto-advance
		autoAdvanceJob?.cancel()
		_playbackState.value = SlideshowPlaybackState()
		loadSlideshowItems()
	}

	/**
	 * Load content on HomeFragment creation
	 * Always fetches fresh random items - no caching
	 */
	fun loadInitialContent() {
		loadSlideshowItems()
	}

	/**
	 * Navigate to the next slide
	 */
	fun nextSlide() {
		if (_playbackState.value.isTransitioning) return
		if (items.isEmpty()) return

		val currentIndex = _playbackState.value.currentIndex
		val nextIndex = (currentIndex + 1) % items.size

		_playbackState.value = _playbackState.value.copy(
			currentIndex = nextIndex,
			isTransitioning = true
		)

		val config = getConfig()
		viewModelScope.launch {
			delay(config.fadeTransitionDurationMs)
			_playbackState.value = _playbackState.value.copy(isTransitioning = false)
			// Preload adjacent images after transition completes
			preloadAdjacentImages(nextIndex)
			// Reset the auto-advance timer after manual or automatic navigation
			resetAutoAdvanceTimer()
		}
	}

	/**
	 * Navigate to the previous slide
	 */
	fun previousSlide() {
		if (_playbackState.value.isTransitioning) return
		if (items.isEmpty()) return

		val currentIndex = _playbackState.value.currentIndex
		val previousIndex = if (currentIndex == 0) items.size - 1 else currentIndex - 1

		_playbackState.value = _playbackState.value.copy(
			currentIndex = previousIndex,
			isTransitioning = true
		)

		val config = getConfig()
		viewModelScope.launch {
			delay(config.fadeTransitionDurationMs)
			_playbackState.value = _playbackState.value.copy(isTransitioning = false)
			// Preload adjacent images after transition completes
			preloadAdjacentImages(previousIndex)
			// Reset the auto-advance timer after manual navigation
			resetAutoAdvanceTimer()
		}
	}

	/**
	 * Preload images for slides adjacent to the current one.
	 * This prevents flickering when navigating between slides by ensuring
	 * images are already cached before they're displayed.
	 * 
	 * @param currentIndex The index of the currently displayed slide
	 */
	private fun preloadAdjacentImages(currentIndex: Int) {
		if (items.isEmpty()) return
		
		viewModelScope.launch(Dispatchers.IO) {
			val indicesToPreload = mutableSetOf<Int>()
			
			// Preload current slide first (highest priority)
			indicesToPreload.add(currentIndex)
			
			// Preload next slide
			val nextIndex = (currentIndex + 1) % items.size
			indicesToPreload.add(nextIndex)
			
			// Preload previous slide
			val previousIndex = if (currentIndex == 0) items.size - 1 else currentIndex - 1
			indicesToPreload.add(previousIndex)
			
			// Optionally preload one more slide ahead for smoother auto-advance
			val nextNextIndex = (nextIndex + 1) % items.size
			indicesToPreload.add(nextNextIndex)
			
			// Preload all the images in parallel
			indicesToPreload.forEach { index ->
				val item = items.getOrNull(index) ?: return@forEach
				
				// Preload backdrop
				item.backdropUrl?.let { url ->
					try {
						val request = ImageRequest.Builder(context)
							.data(url)
							.build()
						imageLoader.enqueue(request)
					} catch (e: Exception) {
						Timber.d("Failed to preload backdrop for item ${item.title}: ${e.message}")
					}
				}
				
				// Preload logo
				item.logoUrl?.let { url ->
					try {
						val request = ImageRequest.Builder(context)
							.data(url)
							.build()
						imageLoader.enqueue(request)
					} catch (e: Exception) {
						Timber.d("Failed to preload logo for item ${item.title}: ${e.message}")
					}
				}
			}
		}
	}

	/**
	 * Refresh background items (not currently visible or adjacent) with new random selections.
	 * This provides variety when regaining focus without causing flickering on the current slide.
	 * Keeps the current item and its adjacent items (previous and next) unchanged.
	 * 
	 * Multi-server support: fetches from all servers when multiple are logged in.
	 */
	private fun refreshBackgroundItems() {
		if (items.isEmpty()) return
		
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val currentIndex = _playbackState.value.currentIndex
				val config = getConfig()
				val contentType = userSettingPreferences[UserSettingPreferences.mediaBarContentType]
				
				// Calculate which indices to keep (current, previous, next)
				val indicesToKeep = mutableSetOf<Int>()
				indicesToKeep.add(currentIndex)
				indicesToKeep.add((currentIndex + 1) % items.size)
				indicesToKeep.add(if (currentIndex == 0) items.size - 1 else currentIndex - 1)
				
				// Only refresh if we have more than 3 items (otherwise all are adjacent)
				if (items.size <= 3) return@launch
				
				// Calculate how many new items we need to fetch
				val itemsToReplace = items.size - indicesToKeep.size
				
				// Fetch new random items (multi-server aware)
				val newItemsWithApiClients: List<ItemWithApiClient> = run {
					val loggedInServers = multiServerRepository.getLoggedInServers()
					val useMultiServer = loggedInServers.size > 1
					if (useMultiServer) {
						val itemsPerServer = (itemsToReplace / loggedInServers.size).coerceAtLeast(2)
						loggedInServers.map { session ->
							async {
								try {
									val serverItems = when (contentType) {
										"movies" -> fetchItemsFromServer(session.apiClient, BaseItemKind.MOVIE, itemsPerServer)
										"tv" -> fetchItemsFromServer(session.apiClient, BaseItemKind.SERIES, itemsPerServer)
										else -> {
											val movies = async { fetchItemsFromServer(session.apiClient, BaseItemKind.MOVIE, itemsPerServer / 2 + 1) }
											val shows = async { fetchItemsFromServer(session.apiClient, BaseItemKind.SERIES, itemsPerServer / 2 + 1) }
											movies.await() + shows.await()
										}
									}
									serverItems.map { ItemWithApiClient(it, session.apiClient, session.server.id) }
								} catch (e: Exception) {
									Timber.e(e, "MediaBar refresh: Failed to fetch from server ${session.server.name}")
									emptyList()
								}
							}
						}.awaitAll().flatten()
					} else {
						val serverItems = when (contentType) {
							"movies" -> fetchItemsFromServer(api, BaseItemKind.MOVIE, itemsToReplace)
							"tv" -> fetchItemsFromServer(api, BaseItemKind.SERIES, itemsToReplace)
							else -> {
								val movies = async { fetchItemsFromServer(api, BaseItemKind.MOVIE, itemsToReplace / 2 + 1) }
								val shows = async { fetchItemsFromServer(api, BaseItemKind.SERIES, itemsToReplace / 2 + 1) }
								movies.await() + shows.await()
							}
						}
						serverItems.map { ItemWithApiClient(it, api) }
					}
				}.filter { it.item.backdropImageTags?.isNotEmpty() == true }
					.shuffled()
					.take(itemsToReplace)
						val movies = async { fetchItems(BaseItemKind.MOVIE, itemsToReplace / 2 + 1) }
						val shows = async { fetchItems(BaseItemKind.SERIES, itemsToReplace / 2 + 1) }
						(movies.await().items.orEmpty() + shows.await().items.orEmpty())
					}
				}
					.filter { it.backdropImageTags?.isNotEmpty() == true }

				// Get logged in servers
				val loggedInServers = multiServerRepository.getLoggedInServers()
				val useMultiServer = loggedInServers.size > 1
				
				// Fetch new random items (with multi-server support)
				val newItemsWithApiClients: List<ItemWithApiClient> = if (useMultiServer) {
					val itemsPerServer = (itemsToReplace / loggedInServers.size).coerceAtLeast(2)
					loggedInServers.map { session ->
						async {
							try {
								val serverItems = when (contentType) {
									"movies" -> fetchItemsFromServer(session.apiClient, BaseItemKind.MOVIE, itemsPerServer)
									"tv" -> fetchItemsFromServer(session.apiClient, BaseItemKind.SERIES, itemsPerServer)
									}
										.filter { it.item.backdropImageTags?.isNotEmpty() == true }
										movies.await() + shows.await()
									}
								}
								serverItems.map { ItemWithApiClient(it, session.apiClient, session.server.id) }
									val newSlideItems = newItemsWithApiClients.map { (item, itemApiClient, serverId) ->
										MediaBarSlideItem(
											itemId = item.id,
											serverId = serverId,
											title = item.name.orEmpty(),
											overview = item.overview,
											backdropUrl = item.backdropImageTags?.firstOrNull()?.let { tag ->
												itemApiClient.imageApi.getItemImageUrl(
					val serverItems = when (contentType) {
						"movies" -> fetchItemsFromServer(api, BaseItemKind.MOVIE, itemsToReplace)
						"tv" -> fetchItemsFromServer(api, BaseItemKind.SERIES, itemsToReplace)
						else -> {
							val movies = async { fetchItemsFromServer(api, BaseItemKind.MOVIE, itemsToReplace / 2 + 1) }
							val shows = async { fetchItemsFromServer(api, BaseItemKind.SERIES, itemsToReplace / 2 + 1) }
							movies.await() + shows.await()
						}
												itemApiClient.imageApi.getItemImageUrl(
					serverItems.map { ItemWithApiClient(it, api) }
				}
					.filter { it.item.backdropImageTags?.isNotEmpty() == true }
 
					.shuffled()
					.take(itemsToReplace)
				
				// Convert to MediaBarSlideItem
 
				val newSlideItems = newItems.map { item ->
					MediaBarSlideItem(
						itemId = item.id,
						title = item.name.orEmpty(),
						overview = item.overview,
						backdropUrl = item.backdropImageTags?.firstOrNull()?.let { tag ->
							api.imageApi.getItemImageUrl(
 
				val newSlideItems = newItemsWithApiClients.map { (item, itemApiClient, serverId) ->
					MediaBarSlideItem(
						itemId = item.id,
						serverId = serverId,
						title = item.name.orEmpty(),
						overview = item.overview,
						backdropUrl = item.backdropImageTags?.firstOrNull()?.let { tag ->
							itemApiClient.imageApi.getItemImageUrl(
 
								itemId = item.id,
								imageType = ImageType.BACKDROP,
								tag = tag,
								maxWidth = 1920,
								quality = 90
							)
						},
						logoUrl = item.imageTags?.get(ImageType.LOGO)?.let { tag ->
							itemApiClient.imageApi.getItemImageUrl(
								itemId = item.id,
								imageType = ImageType.LOGO,
								tag = tag,
								maxWidth = 800,
							)
						},
						rating = item.officialRating,
						year = item.productionYear,
						genres = item.genres.orEmpty().take(3),
						runtime = item.runTimeTicks?.let { ticks -> (ticks / 10000) },
						criticRating = item.criticRating?.toInt(),
						communityRating = item.communityRating,
					)
				}
				
				// Build new items list: keep existing items at protected indices, replace others
				val updatedItems = items.toMutableList()
				var newItemIndex = 0
				
				for (i in items.indices) {
					if (!indicesToKeep.contains(i) && newItemIndex < newSlideItems.size) {
						updatedItems[i] = newSlideItems[newItemIndex]
						newItemIndex++
					}
				}
				
				// Update items list
				items = updatedItems
				_state.value = MediaBarState.Ready(items)
				
				// Preload the newly added items in the background
				withContext(Dispatchers.IO) {
					updatedItems.forEachIndexed { index, item ->
						if (!indicesToKeep.contains(index)) {
							// Preload backdrop
							item.backdropUrl?.let { url ->
								try {
									val request = ImageRequest.Builder(context)
										.data(url)
										.build()
									imageLoader.enqueue(request)
								} catch (e: Exception) {
									Timber.d("Failed to preload backdrop for refreshed item ${item.title}: ${e.message}")
								}
							}
							
							// Preload logo
							item.logoUrl?.let { url ->
								try {
									val request = ImageRequest.Builder(context)
										.data(url)
										.build()
									imageLoader.enqueue(request)
								} catch (e: Exception) {
									Timber.d("Failed to preload logo for refreshed item ${item.title}: ${e.message}")
								}
							}
						}
					}
				}
				
				Timber.d("Refreshed ${newItemIndex} background items while keeping ${indicesToKeep.size} adjacent items")
			} catch (e: Exception) {
				Timber.e(e, "Failed to refresh background items: ${e.message}")
			}
		}
	}

	/**
	 * Toggle pause/play state
	 */
	fun togglePause() {
		_playbackState.value = _playbackState.value.copy(
			isPaused = !_playbackState.value.isPaused
		)
	}
}
