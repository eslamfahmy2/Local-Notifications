package com.example.localnotifications.compose.ui.list

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.localnotifications.compose.ui.detail.DetailActivity
import com.example.localnotifications.compose.ui.theme.LocalNotificationsTheme
import com.example.localnotifications.feature.detail.DetailArgs
import com.example.localnotifications.feature.list.ListViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LocalNotificationsTheme {
                ListRoute(
                    viewModel = viewModel,
                    onNavigateToDetail = { id ->
                        startActivity(
                            Intent(this, DetailActivity::class.java)
                                .putExtra(DetailArgs.NOTIFICATION_ID, id),
                        )
                    },
                )
            }
        }
    }
}
