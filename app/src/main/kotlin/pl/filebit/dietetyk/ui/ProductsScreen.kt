package pl.filebit.dietetyk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.launch
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.data.db.EnergyLogEntity
import pl.filebit.dietetyk.data.db.FoodProductEntity

/** Baza produktów z makro + ulubione. Wejście z FAB „Co chcesz dodać?" i z Profilu. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(app: DietetykApp, onBack: () -> Unit) {
    val all by app.database.foodProductDao().observeAll().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<FoodProductEntity?>(null) }
    val scope = rememberCoroutineScope()

    val filtered = remember(all, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) all else all.filter { it.nameNorm.contains(q) || it.name.lowercase().contains(q) }
    }
    val favorites = filtered.filter { it.favorite }
    val byCategory = filtered.filterNot { it.favorite }.groupBy { it.category.ifBlank { "Inne" } }.toSortedMap()

    if (showAdd) AddProductDialog(app) { showAdd = false }
    detail?.let { p -> ProductDetailSheet(app, p, onDismiss = { detail = null }) }

    Column(Modifier.fillMaxSize().background(Palette.Bg).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text("← Produkty", color = Palette.TextDark, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { onBack() })
            Box(Modifier.background(Palette.Green, RoundedCornerShape(12.dp)).clickable { showAdd = true }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("+ Dodaj", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Szukaj produktu…") }, singleLine = true,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp)) {
            if (favorites.isNotEmpty()) {
                item { CategoryHeader("⭐ Ulubione") }
                items(favorites, key = { "f${it.id}" }) { p -> ProductRow(p, onTap = { detail = p }, onStar = { scope.launch { app.database.foodProductDao().setFavorite(p.id, !p.favorite) } }) }
            }
            byCategory.forEach { (cat, list) ->
                item { CategoryHeader("${catEmoji(cat)} ${cat.uppercase()}") }
                items(list, key = { it.id }) { p -> ProductRow(p, onTap = { detail = p }, onStar = { scope.launch { app.database.foodProductDao().setFavorite(p.id, !p.favorite) } }) }
            }
            if (filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 34.sp)
                        Text(if (all.isEmpty()) "Wczytuję produkty…" else "Nie znaleziono: $query", color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                        if (all.isNotEmpty()) Box(Modifier.padding(top = 12.dp).background(Palette.GreenTint, RoundedCornerShape(12.dp)).clickable { showAdd = true }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text("Dodaj ręcznie", color = Palette.GreenDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(text: String) {
    Text(text, color = Palette.Green, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun ProductRow(p: FoodProductEntity, onTap: () -> Unit, onStar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).card(14.dp).background(Palette.Card, RoundedCornerShape(14.dp)).clickable { onTap() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).background(Palette.GreenTint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(catEmoji(p.category), fontSize = 20.sp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(p.name, color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${p.kcal} kcal · B ${fmt(p.proteinG)} · W ${fmt(p.carbsG)} · T ${fmt(p.fatG)} /100g", color = Palette.Muted, fontSize = 12.sp, maxLines = 1)
        }
        Text(if (p.favorite) "★" else "☆", color = if (p.favorite) Palette.Green else Palette.Muted, fontSize = 22.sp, modifier = Modifier.clickable { onStar() }.padding(start = 6.dp))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProductDetailSheet(app: DietetykApp, p: FoodProductEntity, onDismiss: () -> Unit) {
    var grams by remember { mutableStateOf("100") }
    val scope = rememberCoroutineScope()
    val g = grams.toIntOrNull() ?: 0
    val factor = g / 100.0
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Card) {
        Column(Modifier.fillMaxWidth().imePadding().padding(20.dp).padding(bottom = 24.dp)) {
            Text(p.name, color = Palette.TextDark, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("na 100 g surowego produktu", color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroTile("${p.kcal}", "kcal", Palette.Orange, Modifier.weight(1f))
                MacroTile(fmt(p.proteinG), "białko", Palette.Green, Modifier.weight(1f))
                MacroTile(fmt(p.carbsG), "węgle", Palette.Orange, Modifier.weight(1f))
                MacroTile(fmt(p.fatG), "tłuszcz", Palette.Blue, Modifier.weight(1f))
            }
            Text("Zaloguj porcję", color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
            OutlinedTextField(
                value = grams, onValueChange = { grams = it.filter { c -> c.isDigit() } },
                label = { Text("Ile gramów?") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            val kcal = (p.kcal * factor).toInt()
            Box(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .background(if (g in 1..5000) Palette.Green else Palette.Line, RoundedCornerShape(14.dp))
                    .clickable(enabled = g in 1..5000) {
                        scope.launch {
                            app.database.energyLogDao().insert(
                                EnergyLogEntity(
                                    dateMs = System.currentTimeMillis(), kcalConsumed = kcal, isComplete = false,
                                    proteinG = (p.proteinG * factor).toInt(), carbsG = (p.carbsG * factor).toInt(), fatG = (p.fatG * factor).toInt()
                                )
                            )
                            onDismiss()
                        }
                    }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("Zapisz do dziennika · $kcal kcal", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddProductDialog(app: DietetykApp, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var prot by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Inne") }
    val scope = rememberCoroutineScope()
    val num = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowy produkt") },
        text = {
            Column {
                Text("Wartości na 100 g surowego produktu.", color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Nazwa") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(kcal, { kcal = it.filter { c -> c.isDigit() } }, label = { Text("kcal") }, singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = num, modifier = Modifier.weight(1f))
                    OutlinedTextField(prot, { prot = it.replace(',', '.') }, label = { Text("Białko") }, singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = num, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(carb, { carb = it.replace(',', '.') }, label = { Text("Węgle") }, singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = num, modifier = Modifier.weight(1f))
                    OutlinedTextField(fat, { fat = it.replace(',', '.') }, label = { Text("Tłuszcz") }, singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = num, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(cat, { cat = it }, label = { Text("Kategoria") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val k = kcal.toIntOrNull()
                if (name.isNotBlank() && k != null) {
                    scope.launch {
                        app.database.foodProductDao().insert(
                            FoodProductEntity(
                                name = name.trim(), nameNorm = pl.filebit.dietetyk.data.db.FoodProductSeed.normalize(name),
                                kcal = k, proteinG = prot.toDoubleOrNull() ?: 0.0, carbsG = carb.toDoubleOrNull() ?: 0.0,
                                fatG = fat.toDoubleOrNull() ?: 0.0, category = cat.trim().ifBlank { "Inne" }, source = "user"
                            )
                        )
                    }
                    onDismiss()
                }
            }) { Text("Dodaj") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@Composable
private fun MacroTile(value: String, label: String, tint: Color, modifier: Modifier) {
    Column(modifier.background(Palette.GreenTint, RoundedCornerShape(12.dp)).padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = tint, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = Palette.Muted, fontSize = 10.sp)
    }
}

private fun fmt(d: Double): String = (if (d % 1.0 == 0.0) "%.0f" else "%.1f").format(d)

private fun catEmoji(cat: String): String = when {
    cat.contains("warzyw", true) -> "🥦"
    cat.contains("owoc", true) -> "🍎"
    cat.contains("ryb", true) -> "🐟"
    cat.contains("mię", true) || cat.contains("mie", true) || cat.contains("drób", true) || cat.contains("drob", true) -> "🍗"
    cat.contains("nabia", true) || cat.contains("ser", true) || cat.contains("mleko", true) -> "🥛"
    cat.contains("zbo", true) || cat.contains("pieczyw", true) || cat.contains("kasz", true) || cat.contains("makaron", true) || cat.contains("ryż", true) -> "🌾"
    cat.contains("tłuszcz", true) || cat.contains("tluszcz", true) || cat.contains("olej", true) || cat.contains("oliw", true) -> "🫒"
    cat.contains("orzech", true) || cat.contains("nasion", true) -> "🥜"
    cat.contains("jaj", true) -> "🥚"
    cat.contains("strącz", true) || cat.contains("stracz", true) -> "🫘"
    cat.contains("napó", true) || cat.contains("napo", true) -> "🥤"
    cat.contains("słody", true) || cat.contains("slody", true) -> "🍫"
    else -> "🍽️"
}
