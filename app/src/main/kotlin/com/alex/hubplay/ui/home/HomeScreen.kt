package com.alex.hubplay.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Placeholder. The real Home will fetch /me/home/layout and render
 * Continue Watching / Next Up / LatestInLibrary / Trending / LiveNow
 * rails — same shape as the web client. For now it just confirms
 * pairing worked and offers a logout escape hatch.
 */
@Composable
fun HomeScreen(onLogOut: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text       = "HubPlay",
                    style      = MaterialTheme.typography.displayMedium,
                    color      = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text       = "Conectado correctamente.\nLas rails y el reproductor llegan en la próxima iteración.",
                    style      = MaterialTheme.typography.bodyLarge,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.height(40.dp))
                TextButton(onClick = onLogOut) { Text("Cerrar sesión") }
            }
        }
    }
}
