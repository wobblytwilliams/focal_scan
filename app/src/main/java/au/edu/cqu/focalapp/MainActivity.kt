package au.edu.cqu.focalapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import au.edu.cqu.focalapp.ui.FocalSamplingScreen
import au.edu.cqu.focalapp.ui.FocalSamplingViewModel
import au.edu.cqu.focalapp.ui.FocalSamplingViewModelFactory
import au.edu.cqu.focalapp.ui.theme.FocalAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as FocalSamplingApplication

        setContent {
            FocalAppRoot(app)
        }
    }
}

@Composable
private fun FocalAppRoot(app: FocalSamplingApplication) {
    FocalAppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val viewModel: FocalSamplingViewModel = viewModel(
                factory = FocalSamplingViewModelFactory(
                    repository = app.repository,
                    timeProvider = app.timeProvider
                )
            )

            FocalSamplingScreen(viewModel = viewModel)
        }
    }
}
