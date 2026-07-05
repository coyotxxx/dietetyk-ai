package pl.filebit.dietetyk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import pl.filebit.dietetyk.ui.AppScaffold
import pl.filebit.dietetyk.ui.DietetykTheme

/**
 * Nawigacja Compose na bazie `design/Dietetyk AI.dc.html`: 5 zakładek
 * (Dziś · Plan · Dietetyk · Postępy · Profil), Dietetyk = serce apki.
 */
class MainActivity : ComponentActivity() {
    private val requestNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DietetykApp
        maybeRequestNotifications()
        setContent {
            MaterialTheme { DietetykTheme { AppScaffold(app) } }
        }
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
