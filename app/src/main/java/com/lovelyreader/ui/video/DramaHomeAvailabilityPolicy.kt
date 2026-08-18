package com.lovelyreader.ui.video

/** Keeps source maintenance internal unless there is no usable source at all. */
internal fun dramaHomeAvailabilityMessage(rootStatus: DramaRootUiState): String? =
    if (rootStatus.root == null && !rootStatus.isRefreshing) "片源暂时连不上，请稍后再试" else null
