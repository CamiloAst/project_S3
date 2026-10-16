package uq.seminario.projects3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import uq.seminario.projects3.ui.ChatbotApp
import uq.seminario.projects3.ui.theme.ProjectS3Theme
import uq.seminario.projects3.viewmodel.ChatbotViewModel

class MainActivity : ComponentActivity() {

    private val chatbotViewModel: ChatbotViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProjectS3Theme {
                ChatbotApp(viewModel = chatbotViewModel)
            }
        }
    }
}