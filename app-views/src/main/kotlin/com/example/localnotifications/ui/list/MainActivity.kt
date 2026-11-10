package com.example.localnotifications.ui.list

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.localnotifications.R
import com.example.localnotifications.databinding.ActivityMainBinding
import com.example.localnotifications.feature.detail.DetailArgs
import com.example.localnotifications.feature.list.ListEffect
import com.example.localnotifications.feature.list.ListIntent
import com.example.localnotifications.feature.list.ListState
import com.example.localnotifications.feature.list.ListViewModel
import com.example.localnotifications.ui.common.applySystemBarInsets
import com.example.localnotifications.ui.common.messageRes
import com.example.localnotifications.ui.detail.DetailActivity
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: ListViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private val adapter = NotificationAdapter { id -> viewModel.onIntent(ListIntent.ItemClicked(id)) }

    // Set when the whole app goes to the background (Home, recents...). Opening the detail
    // screen or rotating does not count, so coming back from those does not reload.
    private var appWasInBackground = false
    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            appWasInBackground = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        binding.recycler.apply {
            layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.list_span_count))
            adapter = this@MainActivity.adapter
            applySystemBarInsets()
        }
        binding.retryButton.setOnClickListener { viewModel.onIntent(ListIntent.Reload) }
        binding.errorBanner.setOnClickListener { viewModel.onIntent(ListIntent.Reload) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.effects.collect(::handle) }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        super.onDestroy()
    }

    override fun onStart() {
        // Sent before super.onStart() so the first render already shows the loading state
        // instead of flashing the empty state for one frame.
        viewModel.onIntent(if (appWasInBackground) ListIntent.Reload else ListIntent.Load)
        appWasInBackground = false
        super.onStart()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_cancel_all -> {
            viewModel.onIntent(ListIntent.CancelAllClicked)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun render(state: ListState) {
        binding.progress.isInvisible = !state.isLoading
        adapter.submitList(state.items)

        val showEmptyState = state.items.isEmpty() && !state.isLoading
        binding.emptyState.isVisible = showEmptyState
        binding.emptyMessage.setText(state.error?.messageRes() ?: R.string.empty_list)

        val error = state.error
        val showBanner = error != null && state.items.isNotEmpty()
        binding.errorBanner.isVisible = showBanner
        if (showBanner) {
            binding.errorBanner.text = getString(R.string.error_banner, getString(error.messageRes()))
        }
    }

    private fun handle(effect: ListEffect) {
        when (effect) {
            is ListEffect.NavigateToDetail -> startActivity(
                Intent(this, DetailActivity::class.java)
                    .putExtra(DetailArgs.NOTIFICATION_ID, effect.id),
            )
            ListEffect.AllCancelled ->
                Snackbar.make(binding.root, R.string.all_cancelled, Snackbar.LENGTH_SHORT).show()
        }
    }
}
