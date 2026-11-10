@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.localnotifications.compose.ui.detail

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.localnotifications.compose.R
import com.example.localnotifications.compose.ui.common.messageRes
import com.example.localnotifications.compose.ui.common.openNotificationSettings
import com.example.localnotifications.compose.ui.theme.LocalNotificationsTheme
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.notifications.permission.NotificationPermission
import com.example.localnotifications.feature.detail.DetailEffect
import com.example.localnotifications.feature.detail.DetailIntent
import com.example.localnotifications.feature.detail.DetailState
import com.example.localnotifications.feature.detail.DetailViewModel
import kotlinx.coroutines.launch

@Composable
fun DetailRoute(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val cancelledMessage = stringResource(R.string.notification_cancelled)

    // rememberSaveable keeps the dialogs open across rotation.
    var confirmDelaySeconds by rememberSaveable { mutableStateOf<Int?>(null) }
    var scheduledDelaySeconds by rememberSaveable { mutableStateOf<Int?>(null) }

    // Whatever the user answers, schedule afterwards: the work is queued either way and the
    // dialog warns when notifications are off.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onIntent(DetailIntent.ScheduleConfirmed)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is DetailEffect.ShowConfirmScheduleDialog -> confirmDelaySeconds = effect.delaySeconds
                    is DetailEffect.ShowScheduledDialog -> scheduledDelaySeconds = effect.delaySeconds
                    DetailEffect.ShowCancelled -> launch { snackbarHostState.showSnackbar(cancelledMessage) }
                }
            }
        }
    }

    DetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSchedule = { viewModel.onIntent(DetailIntent.ScheduleClicked) },
        onCancel = { viewModel.onIntent(DetailIntent.CancelClicked) },
        onRetry = { viewModel.onIntent(DetailIntent.Retry) },
    )

    confirmDelaySeconds?.let { delaySeconds ->
        ConfirmScheduleDialog(
            delaySeconds = delaySeconds,
            onConfirm = {
                confirmDelaySeconds = null
                // Permission is asked only after the user confirms, right before scheduling.
                if (NotificationPermission.isGranted(context)) {
                    viewModel.onIntent(DetailIntent.ScheduleConfirmed)
                } else {
                    permissionLauncher.launch(NotificationPermission.PERMISSION)
                }
            },
            onDismiss = { confirmDelaySeconds = null },
        )
    }

    scheduledDelaySeconds?.let { delaySeconds ->
        ScheduledDialog(delaySeconds = delaySeconds, onDismiss = { scheduledDelaySeconds = null })
    }
}

@Composable
fun DetailScreen(
    state: DetailState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSchedule: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        // Only meaningful while this notification is actually scheduled.
                        TextButton(onClick = onCancel, enabled = state.isScheduled) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
                Box(Modifier.height(4.dp)) {
                    if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val notification = state.notification
            val error = state.error
            when {
                error != null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(error.messageRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                notification != null -> NotificationContent(
                    title = notification.title,
                    onSchedule = onSchedule,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
    }
}

@Composable
private fun NotificationContent(title: String, onSchedule: () -> Unit, modifier: Modifier = Modifier) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        // Title and button side by side so nothing needs scrolling in landscape.
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Button(onClick = onSchedule, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.schedule_this_notification))
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onSchedule, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.schedule_this_notification))
            }
        }
    }
}

@Composable
private fun ConfirmScheduleDialog(delaySeconds: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val delay = pluralStringResource(R.plurals.seconds, delaySeconds, delaySeconds)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_schedule_title)) },
        text = { Text(stringResource(R.string.confirm_schedule_message, delay)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_schedule)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun ScheduledDialog(delaySeconds: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notificationsOn = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val delay = pluralStringResource(R.plurals.seconds, delaySeconds, delaySeconds)
    val message = stringResource(R.string.scheduled_message, delay)
    val warning = stringResource(R.string.notifications_disabled_warning)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scheduled_title)) },
        text = { Text(if (notificationsOn) message else "$message\n\n$warning") },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = if (notificationsOn) {
            null
        } else {
            {
                TextButton(
                    onClick = {
                        context.openNotificationSettings()
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.action_settings)) }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun DetailScreenPreview() {
    LocalNotificationsTheme {
        DetailScreen(
            state = DetailState(
                isLoading = false,
                notification = NotificationItem(4, "Schedule me after 40 seconds.", 40),
                isScheduled = true,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onSchedule = {},
            onCancel = {},
            onRetry = {},
        )
    }
}
