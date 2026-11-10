package com.example.localnotifications.compose.ui.detail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.localnotifications.compose.ui.theme.LocalNotificationsTheme
import com.example.localnotifications.feature.detail.DetailViewModel
import dagger.hilt.android.AndroidEntryPoint

/** The notification id arrives as an Intent extra and reaches the ViewModel via SavedStateHandle. */
@AndroidEntryPoint
class DetailActivity : ComponentActivity() {

    private val viewModel: DetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LocalNotificationsTheme {
                DetailRoute(viewModel = viewModel, onBack = onBackPressedDispatcher::onBackPressed)
            }
        }
    }
}
