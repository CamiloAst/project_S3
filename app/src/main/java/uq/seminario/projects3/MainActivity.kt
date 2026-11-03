package uq.seminario.projects3

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import uq.seminario.projects3.ui.ChatbotApp
import uq.seminario.projects3.ui.theme.ProjectS3Theme
import uq.seminario.projects3.viewmodel.ChatbotViewModel

class MainActivity : ComponentActivity() {

    private val chatbotViewModel: ChatbotViewModel by viewModels()

    // Sin este permiso Android 17+ descarta en silencio las conexiones a la red local y la app
    // contesta siempre con el respaldo local, sin que el backend reciba nada
    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Log.w(
                    TAG,
                    "Permiso de red local denegado: el backend en ${BuildConfig.CARE_API_BASE_URL} " +
                            "no será alcanzable y se usará el respaldo CARE local."
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            requestLocalNetworkPermissionIfNeeded()
        }
        setContent {
            ProjectS3Theme {
                ChatbotApp(viewModel = chatbotViewModel)
            }
        }
    }

    /**
     * Solo debug: el backend de desarrollo vive en la red local (emulador 10.0.2.2 o IP del
     * portátil). El permiso se declara únicamente en el manifest de debug.
     */
    private fun requestLocalNetworkPermissionIfNeeded() {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
        val isGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_LOCAL_NETWORK
        ) == PackageManager.PERMISSION_GRANTED
        if (!isGranted) {
            localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
