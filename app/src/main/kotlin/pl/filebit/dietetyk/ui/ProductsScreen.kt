package pl.filebit.dietetyk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
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

    // Drill-down: null = lista kategorii, "★" = ulubione, inaczej nazwa kategorii.
    var selectedCat by remember { mutableStateOf<String?>(null) }
    val q = query.trim().lowercase()
    val searching = q.isNotBlank()
    val filtered = remember(all, q) {
        if (q.isBlank()) all else all.filter { it.nameNorm.contains(q) || it.name.lowercase().contains(q) }
    }
    // Kategorie z licznikami (posortowane alfabetycznie).
    val categories = remember(all) { all.groupBy { it.category.ifBlank { "Inne" } }.toSortedMap() }
    val favCount = all.count { it.favorite }

    val toggleStar: (FoodProductEntity) -> Unit = { p -> scope.launch { app.database.foodProductDao().setFavorite(p.id, !p.favorite) } }

    var editProduct by remember { mutableStateOf<FoodProductEntity?>(null) }
    if (showAdd) ProductFormDialog(app, null) { showAdd = false }
    editProduct?.let { ep -> ProductFormDialog(app, ep) { editProduct = null } }
    detail?.let { p -> ProductDetailSheet(app, p, onDismiss = { detail = null }, onEdit = { detail = null; editProduct = it }) }

    // Tytuł nagłówka + akcja wstecz zależnie od poziomu.
    val headerTitle = when {
        searching -> "← Produkty"
        selectedCat == "★" -> "← Ulubione"
        selectedCat != null -> "← $selectedCat"
        else -> "← Produkty"
    }
    val onHeaderBack = {
        if (!searching && selectedCat != null) selectedCat = null else onBack()
    }

    Column(Modifier.fillMaxSize().background(Palette.Bg).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(headerTitle, color = Palette.TextDark, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { onHeaderBack() })
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Green, RoundedCornerShape(12.dp)).clickable { showAdd = true }.padding(horizontal = 14.dp, vertical = 8.dp)) {
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
            when {
                // 1) Wyszukiwanie → płaska lista wyników z całej bazy.
                searching -> {
                    items(filtered, key = { it.id }) { p -> ProductRow(p, onTap = { detail = p }, onStar = { toggleStar(p) }) }
                    if (filtered.isEmpty()) item { EmptyState(all.isEmpty(), query) { showAdd = true } }
                }
                // 2) Poziom kategorii → lista folderów (ikona kategorii + nazwa + licznik).
                selectedCat == null -> {
                    if (favCount > 0) item { CategoryFolder("⭐", "Ulubione", favCount) { selectedCat = "★" } }
                    categories.forEach { (cat, list) ->
                        item { CategoryFolder(catEmoji(cat), cat, list.size) { selectedCat = cat } }
                    }
                    if (categories.isEmpty()) item { EmptyState(all.isEmpty(), "") { showAdd = true } }
                }
                // 3) Wewnątrz kategorii → produkty tej kategorii (obecny styl wierszy).
                else -> {
                    val list = if (selectedCat == "★") all.filter { it.favorite } else (categories[selectedCat] ?: emptyList())
                    items(list, key = { it.id }) { p -> ProductRow(p, onTap = { detail = p }, onStar = { toggleStar(p) }) }
                    if (list.isEmpty()) item { EmptyState(false, "") { showAdd = true } }
                }
            }
        }
    }
}

/** Wiersz-folder kategorii: ikona + nazwa + licznik produktów + strzałka. */
@Composable
private fun CategoryFolder(emoji: String, name: String, count: Int, onTap: () -> Unit) {
    val accent = when (categoryHue(name)) {
        1 -> Palette.Orange; 2 -> Palette.Blue; 3 -> Palette.Muted; else -> Palette.Green
    }
    val dark = Palette.isDark
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).card(14.dp).background(Palette.Card, RoundedCornerShape(14.dp)).clickable { onTap() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).background(accent.copy(alpha = if (dark) 0.24f else 0.16f), RoundedCornerShape(14.dp)).border(1.dp, accent.copy(alpha = if (dark) 0.40f else 0.28f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 24.sp) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(name, color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(if (count == 1) "1 produkt" else "$count produktów", color = Palette.Muted, fontSize = 13.sp)
        }
        Text("›", color = Palette.Muted, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
    }
}

@Composable
private fun EmptyState(loading: Boolean, query: String, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🔍", fontSize = 34.sp)
        Text(if (loading) "Wczytuję produkty…" else if (query.isNotBlank()) "Nie znaleziono: $query" else "Brak produktów w tej kategorii", color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        if (!loading) Box(Modifier.padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(Palette.GreenTint, RoundedCornerShape(12.dp)).clickable { onAdd() }.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Dodaj ręcznie", color = Palette.GreenDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategoryHeader(text: String) {
    Text(text, color = Palette.Green, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun ProductRow(p: FoodProductEntity, onTap: () -> Unit, onStar: () -> Unit) {
    val accent = when (categoryHue(p.category)) {
        1 -> Palette.Orange
        2 -> Palette.Blue
        3 -> Palette.Muted
        else -> Palette.Green
    }
    val dark = Palette.isDark
    val tileBg = accent.copy(alpha = if (dark) 0.24f else 0.16f)
    val tileBorder = accent.copy(alpha = if (dark) 0.40f else 0.28f)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).card(14.dp).background(Palette.Card, RoundedCornerShape(14.dp)).clickable { onTap() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).background(tileBg, RoundedCornerShape(14.dp)).border(1.dp, tileBorder, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) { Text(productEmoji(p.name, p.category), fontSize = 22.sp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(p.name, color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${p.kcal} kcal · B ${fmt(p.proteinG)} · W ${fmt(p.carbsG)} · T ${fmt(p.fatG)} /100g", color = Palette.Muted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(
            imageVector = if (p.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (p.favorite) "Ulubiony" else "Dodaj do ulubionych",
            tint = if (p.favorite) Palette.Green else Palette.Muted,
            modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { onStar() }.padding(8.dp).size(22.dp)
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProductDetailSheet(app: DietetykApp, p: FoodProductEntity, onDismiss: () -> Unit, onEdit: (FoodProductEntity) -> Unit) {
    var grams by remember { mutableStateOf("100") }
    val scope = rememberCoroutineScope()
    val g = grams.toIntOrNull() ?: 0
    val factor = g / 100.0
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Card) {
        Column(Modifier.fillMaxWidth().imePadding().padding(20.dp).padding(bottom = 24.dp)) {
            Text(p.name, color = Palette.TextDark, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            val srcLabel = when (p.source) { "user" -> "dodane ręcznie · " ; "scan" -> "zeskanowane · "; "off" -> "z OpenFoodFacts · "; else -> "" }
            Text("$srcLabel"+"na 100 g surowego produktu", color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
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

            // Edycja/usuwanie tylko produktów usera/skanu (seed = baseline, nie ruszać).
            if (p.source != "seed") {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("✏️ Edytuj", color = Palette.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onEdit(p) })
                    Text("🗑 Usuń produkt", color = Palette.Error, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { scope.launch { app.database.foodProductDao().delete(p.id); onDismiss() } })
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProductFormDialog(app: DietetykApp, existing: FoodProductEntity?, onDismiss: () -> Unit) {
    fun d(v: Double) = if (v == 0.0) "" else (if (v % 1.0 == 0.0) "%.0f" else "%.1f").format(v)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kcal by remember { mutableStateOf(existing?.kcal?.takeIf { it > 0 }?.toString() ?: "") }
    var prot by remember { mutableStateOf(existing?.let { d(it.proteinG) } ?: "") }
    var carb by remember { mutableStateOf(existing?.let { d(it.carbsG) } ?: "") }
    var fat by remember { mutableStateOf(existing?.let { d(it.fatG) } ?: "") }
    var cat by remember { mutableStateOf(existing?.category ?: "Inne") }
    val scope = rememberCoroutineScope()
    val num = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nowy produkt" else "Edytuj produkt") },
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
                    val category = cat.trim().let { if (it.isBlank() || it == "Inne") inferCategory(name) else it }
                    scope.launch {
                        val dao = app.database.foodProductDao()
                        if (existing == null) {
                            dao.insert(FoodProductEntity(
                                name = name.trim(), nameNorm = pl.filebit.dietetyk.data.db.FoodProductSeed.normalize(name),
                                kcal = k, proteinG = prot.toDoubleOrNull() ?: 0.0, carbsG = carb.toDoubleOrNull() ?: 0.0,
                                fatG = fat.toDoubleOrNull() ?: 0.0, category = category, source = "user"
                            ))
                        } else {
                            dao.update(existing.copy(
                                name = name.trim(), nameNorm = pl.filebit.dietetyk.data.db.FoodProductSeed.normalize(name),
                                kcal = k, proteinG = prot.toDoubleOrNull() ?: 0.0, carbsG = carb.toDoubleOrNull() ?: 0.0,
                                fatG = fat.toDoubleOrNull() ?: 0.0, category = category
                                // id, source, barcode, favorite, imageUrl — zachowane
                            ))
                        }
                    }
                    onDismiss()
                }
            }) { Text(if (existing == null) "Dodaj" else "Zapisz") }
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
