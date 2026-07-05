package pl.filebit.dietetyk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

/**
 * Ekran startowy — czat z Dietetykiem (serce apki). Docelowo: nawigacja Compose
 * (Czat/Dziś/Plan/Postępy/Profil) na bazie `design/Dietetyk AI.dc.html`.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DietetykApp
        setContent {
            MaterialTheme { ChatScreen(app) }
        }
    }
}
