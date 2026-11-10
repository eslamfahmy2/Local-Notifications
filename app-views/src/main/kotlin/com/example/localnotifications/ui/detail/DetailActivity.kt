package com.example.localnotifications.ui.detail

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.localnotifications.R
import com.example.localnotifications.core.notifications.permission.NotificationPermission
import com.example.localnotifications.databinding.ActivityDetailBinding
import com.example.localnotifications.feature.detail.DetailEffect
import com.example.localnotifications.feature.detail.DetailIntent
import com.example.localnotifications.feature.detail.DetailState
import com.example.localnotifications.feature.detail.DetailViewModel
import com.example.localnotifications.ui.common.applySystemBarInsets
import com.example.localnotifications.ui.common.messageRes
import com.example.localnotifications.ui.common.openNotificationSettings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Receives the notification id as an Intent extra. Hilt/ViewModel expose it to
 * [DetailViewModel] through SavedStateHandle, so this class never reads it directly.
 */
@AndroidEntryPoint
class DetailActivity : AppCompatActivity() {

    private val viewModel: DetailViewModel by viewModels()
    private lateinit var binding: ActivityDetailBinding

    private var isScheduled = false
    private var dialog: AlertDialog? = null

    // Whatever the user answers, schedule afterwards: the work is queued either way and the
    // notification is simply not shown if permission stays denied (the dialog warns about it).
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onIntent(DetailIntent.ScheduleConfirmed)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.content.applySystemBarInsets()

        binding.scheduleButton.setOnClickListener { viewModel.onIntent(DetailIntent.ScheduleClicked) }
        binding.retryButton.setOnClickListener { viewModel.onIntent(DetailIntent.Retry) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.effects.collect(::handle) }
        }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    // Cancel is only meaningful while this notification is actually scheduled.
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_cancel)?.isEnabled = isScheduled
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_cancel -> {
            viewModel.onIntent(DetailIntent.CancelClicked)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun render(state: DetailState) {
        binding.progress.isInvisible = !state.isLoading

        val notification = state.notification
        binding.contentGroup.isVisible = notification != null
        binding.titleLabel.text = notification?.title.orEmpty()

        val error = state.error
        binding.errorGroup.isVisible = error != null
        if (error != null) binding.errorMessage.setText(error.messageRes())

        if (isScheduled != state.isScheduled) {
            isScheduled = state.isScheduled
            invalidateOptionsMenu()
        }
    }

    private fun handle(effect: DetailEffect) {
        when (effect) {
            is DetailEffect.ShowConfirmScheduleDialog -> showConfirmScheduleDialog(effect.delaySeconds)
            is DetailEffect.ShowScheduledDialog -> showScheduledDialog(effect.delaySeconds)
            DetailEffect.ShowCancelled ->
                Snackbar.make(binding.root, R.string.notification_cancelled, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showConfirmScheduleDialog(delaySeconds: Int) {
        val delay = resources.getQuantityString(R.plurals.seconds, delaySeconds, delaySeconds)
        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_schedule_title)
            .setMessage(getString(R.string.confirm_schedule_message, delay))
            .setPositiveButton(R.string.action_schedule) { _, _ -> scheduleConfirmed() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // Permission is asked only after the user confirms, right before scheduling.
    private fun scheduleConfirmed() {
        if (NotificationPermission.isGranted(this)) {
            viewModel.onIntent(DetailIntent.ScheduleConfirmed)
        } else {
            permissionLauncher.launch(NotificationPermission.PERMISSION)
        }
    }

    private fun showScheduledDialog(delaySeconds: Int) {
        val delay = resources.getQuantityString(R.plurals.seconds, delaySeconds, delaySeconds)
        val notificationsOn = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val message = buildString {
            append(getString(R.string.scheduled_message, delay))
            if (!notificationsOn) append("\n\n").append(getString(R.string.notifications_disabled_warning))
        }

        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.scheduled_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .apply {
                if (!notificationsOn) setNeutralButton(R.string.action_settings) { _, _ -> openNotificationSettings() }
            }
            .show()
    }
}
