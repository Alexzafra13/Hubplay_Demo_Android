package com.alex.hubplay.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.home.components.HeroSection
import com.alex.hubplay.ui.home.components.HomeRail
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.home.components.TopNav

/**
 * Home — the rich version.
 *
 * Layout:
 *   [TopNav (sticky)]
 *   [Hero (rotating spotlight; focus-driven preview)]
 *   [Continuar viendo rail]
 *   [Lo último en tu librería rail]
 *   [Tendencias rail]
 *   [En directo ahora rail]
 *
 * Vertical scroll uses verticalScroll instead of LazyColumn because
 * the Hero is intentionally tall and its state (auto-rotate timer,
 * focused override) shouldn't reset when offscreen. With <10 rails the
 * perf cost is negligible.
 */
@Composable
fun HomeScreen(
    viewModel:   HomeViewModel,
    onPlayItem:  (itemId: String, resumePosSec: Long) -> Unit,
    onLogOut:    () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val focused by viewModel.focusedItem.collectAsState()
    var selectedTab by remember { mutableStateOf(Tab.Home) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            TopNav(
                selectedTab    = selectedTab,
                onTabSelected  = { selectedTab = it },
                onSearch       = { /* Search modal — next sprint */ },
                onLogOut       = onLogOut,
            )

            when {
                ui.isLoading && ui.data.continueWatching.isEmpty()
                                                && ui.data.trending.isEmpty() -> CenteredSpinner()
                ui.error != null                                                -> ErrorBanner(
                    message = ui.error!!,
                    onRetry = viewModel::refresh,
                )
                else                                                            -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    HeroSection(
                        spotlight        = ui.data.hero,
                        focusedOverride  = focused,
                        onPlay           = { onPlayItem(it.id, it.resumePosSec) },
                        onDetails        = { /* Detail screen — next sprint */ },
                    )

                    HomeRail(
                        title     = "Continuar viendo",
                        items     = ui.data.continueWatching,
                        onFocused = viewModel::onCardFocused,
                        onClick   = { onPlayItem(it.id, it.resumePosSec) },
                    )
                    HomeRail(
                        title     = "Lo último en tu librería",
                        items     = ui.data.latest,
                        onFocused = viewModel::onCardFocused,
                        onClick   = { onPlayItem(it.id, 0L) },
                    )
                    HomeRail(
                        title     = "Tendencias",
                        items     = ui.data.trending,
                        onFocused = viewModel::onCardFocused,
                        onClick   = { onPlayItem(it.id, 0L) },
                    )
                    HomeRail(
                        title     = "En directo ahora",
                        items     = ui.data.liveNow,
                        onFocused = viewModel::onCardFocused,
                        onClick   = { onPlayItem(it.id, 0L) },
                    )
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}
