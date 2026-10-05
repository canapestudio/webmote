package io.github.krank56.webmote.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.SavedTv

/** The display name of the TV with [id], or a generic "TV" when it isn't known. */
@Composable
fun tvName(tvs: List<SavedTv>, id: String?): String =
    tvs.firstOrNull { it.id == id }?.name ?: stringResource(R.string.tv_generic_name)

/** The top bar shared by the remote and the TV's state screens: the active TV's name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvTopBar(tvs: List<SavedTv>, activeTvId: String?) {
    TopAppBar(
        title = { Text(tvName(tvs, activeTvId), maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}
