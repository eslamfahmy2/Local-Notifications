@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.localnotifications.compose.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.localnotifications.compose.R
import com.example.localnotifications.compose.ui.common.messageRes
import com.example.localnotifications.compose.ui.theme.LocalNotificationsTheme
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.feature.list.ListEffect
import com.example.localnotifications.feature.list.ListError
import com.example.localnotifications.feature.list.ListIntent
import com.example.localnotifications.feature.list.ListState
import com.example.localnotifications.feature.list.ListViewModel
import kotlinx.coroutines.launch

/** Stateful entry point: connects the ViewModel, lifecycle and side effects to the stateless [ListScreen]. */
@Composable
fun ListRoute(
    viewModel: ListViewModel,
    onNavigateToDetail: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val allCancelledMessage = stringResource(R.string.all_cancelled)

    // Reload only after the whole app was in the background (Home, recents...). Opening the
    // detail screen or rotating does not count, so coming back from those does not reload.
    var appWasInBackground by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP, lifecycleOwner = ProcessLifecycleOwner.get()) {
        appWasInBackground = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onIntent(if (appWasInBackground) ListIntent.Reload else ListIntent.Load)
        appWasInBackground = false
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is ListEffect.NavigateToDetail -> onNavigateToDetail(effect.id)
                    ListEffect.AllCancelled -> launch { snackbarHostState.showSnackbar(allCancelledMessage) }
                }
            }
        }
    }

    ListScreen(state = state, snackbarHostState = snackbarHostState, onIntent = viewModel::onIntent)
}

@Composable
fun ListScreen(
    state: ListState,
    snackbarHostState: SnackbarHostState,
    onIntent: (ListIntent) -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        TextButton(onClick = { onIntent(ListIntent.CancelAllClicked) }) {
                            Text(stringResource(R.string.action_cancel_all))
                        }
                    },
                )
                // Reserve the height so the content does not jump when loading starts or stops.
                Box(Modifier.height(4.dp)) {
                    if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                val error = state.error
                if (error != null && state.items.isNotEmpty()) {
                    ErrorBanner(error = error, onClick = { onIntent(ListIntent.Reload) })
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = state.items, key = { it.id }) { item ->
                        NotificationCard(item = item, onClick = { onIntent(ListIntent.ItemClicked(item.id)) })
                    }
                }
            }
            if (state.items.isEmpty() && !state.isLoading) {
                EmptyState(error = state.error, onRetry = { onIntent(ListIntent.Reload) })
            }
        }
    }
}

@Composable
private fun NotificationCard(item: NotificationItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            val delay = pluralStringResource(R.plurals.seconds, item.delaySeconds, item.delaySeconds)
            Text(
                text = stringResource(R.string.item_delay, delay),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorBanner(error: ListError, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Text(
            text = stringResource(R.string.error_banner, stringResource(error.messageRes())),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun EmptyState(error: ListError?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(error?.messageRes() ?: R.string.empty_list),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ListScreenPreview() {
    LocalNotificationsTheme {
        ListScreen(
            state = ListState(
                items = listOf(
                    NotificationItem(1, "Schedule me after 10 seconds.", 10),
                    NotificationItem(2, "Schedule me after 20 seconds.", 20),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ListScreenErrorPreview() {
    LocalNotificationsTheme {
        ListScreen(
            state = ListState(error = ListError.Network),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
        )
    }
}
