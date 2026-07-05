package pl.filebit.dietetyk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import pl.filebit.dietetyk.ui.AppScaffold

/**
 * Nawigacja Compose na bazie `design/Dietetyk AI.dc.html`: 5 zakładek
 * (Dziś · Plan · Dietetyk · Postępy · Profil), Dietetyk = serce apki.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DietetykApp
        setContent {
            MaterialTheme { AppScaffold(app) }
        }
    }
}
