package cc.hafa.latr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.hafa.latr.ui.TodoScreen
import cc.hafa.latr.ui.TodoViewModel
import cc.hafa.latr.ui.TodoViewModelFactory
import cc.hafa.latr.ui.theme.LatrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LatrTheme {
                val app = application as LatrApplication
                val viewModel: TodoViewModel = viewModel(
                    factory = TodoViewModelFactory(app.storeHolder, app.snoozeStatsStore)
                )
                TodoScreen(
                    viewModel = viewModel,
                    authManager = app.authManager,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
