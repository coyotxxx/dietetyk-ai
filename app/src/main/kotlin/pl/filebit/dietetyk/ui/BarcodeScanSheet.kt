package pl.filebit.dietetyk.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.ai.OffProduct
import pl.filebit.dietetyk.data.db.EnergyLogEntity
import pl.filebit.dietetyk.data.db.FoodProductEntity
import pl.filebit.dietetyk.data.db.FoodProductSeed

/** Stan skanowania kodu kreskowego. */
sealed class ScanState {
    object Scanning : ScanState()
    data class Found(val barcode: String, val product: OffProduct) : ScanState()
    data class NotFound(val barcode: String) : ScanState()
    data class Failed(val message: String) : ScanState()
}

/** Uruchamia skaner Google (bez uprawnienia CAMERA — UI dostarcza Play Services). */
fun launchBarcodeScan(context: Context, onCode: (String?) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { onCode(it.rawValue) }
        .addOnCanceledListener { onCode(null) }
        .addOnFailureListener { onCode(null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeResultSheet(app: DietetykApp, state: ScanState, onDismiss: () -> Unit, onAskDietitian: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Card) {
        Column(Modifier.fillMaxWidth().imePadding().padding(20.dp).padding(bottom = 24.dp)) {
            when (state) {
                is ScanState.Scanning -> {
                    Text("Sprawdzam produkt…", color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Szukam kodu w bazie OpenFoodFacts", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
                is ScanState.Found -> FoundContent(app, state, onDismiss)
                is ScanState.NotFound -> {
                    Text("Nie znam tego kodu 🤔", color = Palette.TextDark, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Kodu ${state.barcode} nie ma w bazie OpenFoodFacts.", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    ActionButton("Dodaj ręcznie", Palette.Green) { onDismiss() /* user doda przez „+ Dodaj" w bazie */ }
                    Box(Modifier.padding(top = 8.dp))
                    ActionButton("Zapytaj dietetyka", Palette.GreenTint, textColor = Palette.GreenDark) {
                        onAskDietitian("Zeskanowałem kod kreskowy ${state.barcode}, ale nie ma go w bazie. Pomożesz oszacować wartości?")
                        onDismiss()
                    }
                }
                is ScanState.Failed -> {
                    Text("Nie udało się zeskanować", color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(state.message, color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun FoundContent(app: DietetykApp, state: ScanState.Found, onDismiss: () -> Unit) {
    val p = state.product
    var grams by remember { mutableStateOf("100") }
    val scope = rememberCoroutineScope()
    val g = grams.toIntOrNull() ?: 0
    val factor = g / 100.0

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (!p.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = p.imageUrl, contentDescription = p.name,
                modifier = Modifier.size(64.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(4.dp)
            )
        }
        Column(Modifier.weight(1f).padding(start = if (p.imageUrl.isNullOrBlank()) 0.dp else 12.dp)) {
            Text("ROZPOZNANO PRODUKT", color = Palette.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(p.name, color = Palette.TextDark, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
    Text("na 100 g produktu", color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MacroTileB("${p.kcal}", "kcal", Palette.Orange)
        MacroTileB(fmtB(p.proteinG), "białko", Palette.Green)
        MacroTileB(fmtB(p.carbsG), "węgle", Palette.Orange)
        MacroTileB(fmtB(p.fatG), "tłuszcz", Palette.Blue)
    }

    androidx.compose.material3.OutlinedTextField(
        value = grams, onValueChange = { grams = it.filter { c -> c.isDigit() } },
        label = { Text("Ile gramów zjadłeś?") }, singleLine = true, shape = RoundedCornerShape(12.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
    )
    val kcal = (p.kcal * factor).toInt()
    ActionButton("Zapisz do dziennika · $kcal kcal", if (g in 1..5000) Palette.Green else Palette.Line, topPad = 12.dp, enabled = g in 1..5000) {
        scope.launch {
            app.database.energyLogDao().insert(
                EnergyLogEntity(
                    dateMs = System.currentTimeMillis(), kcalConsumed = kcal, isComplete = false,
                    proteinG = (p.proteinG * factor).toInt(), carbsG = (p.carbsG * factor).toInt(), fatG = (p.fatG * factor).toInt()
                )
            )
            saveToBase(app, state); onDismiss()
        }
    }
    ActionButton("Tylko dodaj do bazy", Palette.GreenTint, textColor = Palette.GreenDark, topPad = 8.dp) {
        scope.launch { saveToBase(app, state); onDismiss() }
    }
}

/** Zapisuje zeskanowany produkt do bazy (jeśli jeszcze go nie ma po kodzie). */
private suspend fun saveToBase(app: DietetykApp, state: ScanState.Found) {
    val p = state.product
    runCatching {
        app.database.foodProductDao().insert(
            FoodProductEntity(
                name = p.name, nameNorm = FoodProductSeed.normalize(p.name),
                kcal = p.kcal, proteinG = p.proteinG, carbsG = p.carbsG, fatG = p.fatG,
                category = "Inne", source = "scan", barcode = state.barcode, imageUrl = p.imageUrl
            )
        )
    }
}

@Composable
private fun ActionButton(text: String, bg: Color, textColor: Color = Color.White, topPad: androidx.compose.ui.unit.Dp = 0.dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(top = topPad).background(bg, RoundedCornerShape(14.dp)).clickable(enabled = enabled) { onClick() }.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MacroTileB(value: String, label: String, tint: Color) {
    Column(Modifier.weight(1f).background(Palette.GreenTint, RoundedCornerShape(12.dp)).padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = tint, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = Palette.Muted, fontSize = 10.sp)
    }
}

private fun fmtB(d: Double): String = (if (d % 1.0 == 0.0) "%.0f" else "%.1f").format(d)
