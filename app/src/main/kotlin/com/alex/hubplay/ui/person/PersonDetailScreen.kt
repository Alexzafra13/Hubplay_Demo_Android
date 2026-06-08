package com.alex.hubplay.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.data.PersonDetail
import com.alex.hubplay.ui.catalog.PortraitCatalogCard
import com.alex.hubplay.ui.components.BackPill
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.BgElevated

/**
 * Person profile + filmography. Reached by tapping a cast/crew card on
 * the Detail screen. Header is a circular avatar + name + role; the body
 * is the same poster grid the catalogue uses, so tapping a film jumps to
 * its real Detail / Series screen via [onOpenItem].
 */
@Composable
fun PersonDetailScreen(
    viewModel:  PersonDetailViewModel,
    onOpenItem: (String, MediaKind) -> Unit,
    onBack:     () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        when {
            ui.isLoading     -> CenteredSpinner()
            ui.error != null -> ErrorBanner(message = ui.error!!, onRetry = viewModel::load)
            ui.person != null -> PersonContent(
                person     = ui.person!!,
                onOpenItem = onOpenItem,
                onBack     = onBack,
            )
        }
    }
}

@Composable
private fun PersonContent(
    person:     PersonDetail,
    onOpenItem: (String, MediaKind) -> Unit,
    onBack:     () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PersonHeader(person = person, onBack = onBack)

        if (person.filmography.isEmpty()) {
            EmptyFilmography()
        } else {
            LazyVerticalGrid(
                columns               = GridCells.Adaptive(minSize = 150.dp),
                contentPadding        = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(14.dp),
                modifier              = Modifier.fillMaxSize(),
            ) {
                items(person.filmography, key = { it.id }) { item ->
                    PortraitCatalogCard(item, onOpenItem)
                }
            }
        }
    }
}

@Composable
private fun PersonHeader(person: PersonDetail, onBack: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackPill(onBack = onBack)
        Spacer(Modifier.width(20.dp))
        Box(
            modifier         = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(BgElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (person.imageUrl != null) {
                AsyncImage(
                    model              = person.imageUrl,
                    contentDescription = person.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text  = person.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text       = person.name,
                style      = MaterialTheme.typography.headlineMedium,
                color      = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            val typeLabel = when (person.type?.lowercase()) {
                "actor"    -> stringResource(R.string.person_role_actor)
                "director" -> stringResource(R.string.person_role_director)
                "writer"   -> stringResource(R.string.person_role_writer)
                else       -> person.type
            }
            typeLabel?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text  = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.padding(top = 6.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun EmptyFilmography() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text  = stringResource(R.string.person_no_filmography),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
