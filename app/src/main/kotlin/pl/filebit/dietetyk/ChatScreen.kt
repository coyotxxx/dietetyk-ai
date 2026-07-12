package pl.filebit.dietetyk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.filebit.dietetyk.data.db.ChatMessageEntity
import pl.filebit.dietetyk.ui.ActionCard
import pl.filebit.dietetyk.ai.ClaudeHttpApi
import pl.filebit.dietetyk.ai.DietToolHandler
import pl.filebit.dietetyk.ai.DietitianConversation
import pl.filebit.dietetyk.core.aicontract.CareStage
import pl.filebit.dietetyk.core.aicontract.CareState
import pl.filebit.dietetyk.core.aicontract.DietitianPrompt
import pl.filebit.dietetyk.core.aicontract.InterviewTopic
import pl.filebit.dietetyk.ui.Palette

internal data class UiMsg(
    val fromUser: Boolean, val text: String,
    val actions: List<String> = emptyList(),
    val cards: List<pl.filebit.dietetyk.ui.CardData> = emptyList(),
    val imageUri: String? = null
)

private val ACTIONS_RE = Regex("""\[\[\s*akcje\s*:\s*(.+?)\s*]]""", RegexOption.IGNORE_CASE)
private val CARD_RE = Regex("""\[\[card\]\]\s*(\{.*?\})\s*\[\[/card\]\]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

/** Wyodrębnij karty `[[card]]{...}[[/card]]` i szybkie odpowiedzi `[[akcje: A|B|C]]`, oczyść tekst. */
private fun parseAiReply(raw: String): UiMsg {
    val cards = CARD_RE.findAll(raw).mapNotNull { m ->
        runCatching {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(m.groupValues[1]).jsonObject
            pl.filebit.dietetyk.ui.CardData(obj["type"]?.jsonPrimitive?.content ?: "generic", obj)
        }.getOrNull()
    }.toList()
    var text = CARD_RE.replace(raw, "").trim()
    val am = ACTIONS_RE.find(text)
    val actions = am?.groupValues?.get(1)?.split("|")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    text = ACTIONS_RE.replace(text, "").trim()
    return UiMsg(false, if (text.isBlank() && cards.isNotEmpty()) "" else text.ifBlank { "…" }, actions, cards)
}

/** Stan i trwałość rozmowy — przeżywa zmianę zakładki (VM) i restart (Room + prefs). */
internal class ChatViewModel(private val app: DietetykApp) : ViewModel() {
    val messages = mutableStateListOf<UiMsg>()
    val history = mutableListOf<JsonObject>()
    var sending by mutableStateOf(false); private set
    private val handler = DietToolHandler(app)

    init {
        viewModelScope.launch {
            val stored = app.database.chatMessageDao().all()
            if (stored.isEmpty()) {
                messages.add(UiMsg(false, "Cześć! Jestem Twoim dietetykiem. Opowiedz mi o sobie — co chciałbyś osiągnąć?"))
            } else {
                stored.forEach { messages.add(it.toUiMsg()) }
                runCatching {
                    kotlinx.serialization.json.Json.parseToJsonElement(app.settings.chatHistoryJson).jsonArray
                        .forEach { history.add(it.jsonObject) }
                }
                // Pas bezpieczeństwa: odzyskaj historię od razu przy wczytaniu (nie tylko przy send).
                DietitianConversation.sanitizeHistory(history)
            }
        }
    }

    private fun ChatMessageEntity.toUiMsg(): UiMsg {
        val cards = runCatching {
            if (cardsJson.isBlank()) emptyList()
            else kotlinx.serialization.json.Json.parseToJsonElement(cardsJson).jsonArray.mapNotNull { it as? JsonObject }
                .map { pl.filebit.dietetyk.ui.CardData(it["type"]?.jsonPrimitive?.content ?: "generic", it) }
        }.getOrDefault(emptyList())
        return UiMsg(fromUser, text, if (actionsCsv.isBlank()) emptyList() else actionsCsv.split("|"), cards, imageUri.takeIf { it.isNotBlank() })
    }

    private suspend fun persist(msg: UiMsg) {
        val cardsJson = if (msg.cards.isEmpty()) "" else buildJsonArray { msg.cards.forEach { add(it.json) } }.toString()
        app.database.chatMessageDao().insert(
            ChatMessageEntity(fromUser = msg.fromUser, text = msg.text, actionsCsv = msg.actions.joinToString("|"), cardsJson = cardsJson, imageUri = msg.imageUri ?: "", createdAt = System.currentTimeMillis())
        )
    }

    private fun saveHistory() { app.settings.chatHistoryJson = buildJsonArray { history.forEach { add(it) } }.toString() }

    fun send(raw: String, apiKey: String) {
        val text = raw.trim()
        if (text.isEmpty() || sending) return
        val userMsg = UiMsg(true, text); messages.add(userMsg); sending = true
        viewModelScope.launch {
            persist(userMsg)
            val reply = runCatching { sendToDietitian(app, history, text, handler, apiKey) }.getOrElse { friendlyError(it) }
            val aiMsg = parseAiReply(reply); messages.add(aiMsg); persist(aiMsg); saveHistory()
            sending = false
        }
    }

    /** Zamienia surowy wyjątek sieci/API na ludzki komunikat — technicznego stacktrace user nie powinien widzieć. */
    private fun friendlyError(e: Throwable): String {
        val msg = (e.message ?: "").lowercase()
        val offline = e is java.io.IOException || "unable to resolve host" in msg || "no address associated" in msg ||
            "timeout" in msg || "timed out" in msg || "failed to connect" in msg || "network" in msg
        return if (offline) {
            "Jestem chwilowo poza zasięgiem 🌐 — sprawdź połączenie z internetem i napisz jeszcze raz. " +
                "Twój plan, posiłki i postępy działają też offline."
        } else {
            "Coś mi nie zadziałało po mojej stronie 😔 — spróbuj napisać jeszcze raz za chwilę."
        }
    }

    /** Reset rozmowy w pamięci (bazę/prefs czyści wywołujący). Reseeduje powitanie + usuwa zdjęcia. */
    fun resetInMemory() {
        messages.clear(); history.clear()
        messages.add(UiMsg(false, "Cześć! Jestem Twoim dietetykiem. Opowiedz mi o sobie — co chciałbyś osiągnąć?"))
        pl.filebit.dietetyk.ImageUtil.clearChatImages(app)
    }

    /** Wysyła zdjęcie posiłku. [imagePath] = trwały plik w filesDir (miniatura + źródło base64 do API). */
    fun sendPhoto(imagePath: String?, apiKey: String) {
        if (sending) return
        val userMsg = UiMsg(true, "📷 Zdjęcie posiłku", imageUri = imagePath); messages.add(userMsg); sending = true
        viewModelScope.launch {
            persist(userMsg)
            val b64 = imagePath?.let { pl.filebit.dietetyk.ImageUtil.base64FromFile(it) }
            val reply = if (b64 == null) "Nie udało się odczytać zdjęcia — spróbuj jeszcze raz."
            else runCatching {
                sendToDietitian(app, history, "To zdjęcie mojego posiłku. Rozpoznaj co to, oszacuj kalorie i makro, i zapytaj czy zapisać.", handler, apiKey, imageB64 = b64)
            }.getOrElse { "Coś poszło nie tak przy analizie zdjęcia: ${it.message}" }
            val aiMsg = parseAiReply(reply); messages.add(aiMsg); persist(aiMsg); saveHistory()
            sending = false
        }
    }
}

/** Etykieta akcji-mostu: gdy AI ją zaproponuje, kliknięcie OTWIERA bazę produktów zamiast wysyłać tekst. */
private const val OPEN_PRODUCTS_ACTION = "Otwórz bazę produktów"
/** Akcja onboardingu: otwiera szybki picker smaku (siatka ❤️/🚫) zamiast wysyłać tekst do AI. */
private const val OPEN_TASTE_PICKER_ACTION = "Otwórz i zaznacz produkty"

@Composable
fun ChatScreen(app: DietetykApp, modifier: Modifier = Modifier, onBrowseProducts: () -> Unit = {}, onOpenTastePicker: () -> Unit = {}) {
    var apiKey by remember { mutableStateOf(app.settings.apiKey) }
    if (apiKey.isBlank()) {
        ApiKeyGate(onSaved = { app.settings.apiKey = it; apiKey = it })
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: ChatViewModel = viewModel { ChatViewModel(app) }
    var input by remember { mutableStateOf("") }
    // Akcja z bąbelka/podpowiedzi: sentinel „Otwórz bazę produktów" nawiguje do bazy (most z KROKU 6),
    // każda inna akcja to zwykła szybka odpowiedź wysyłana do dietetyka.
    val onAction: (String) -> Unit = { a ->
        when {
            a.equals(OPEN_TASTE_PICKER_ACTION, ignoreCase = true) -> onOpenTastePicker()
            a.equals(OPEN_PRODUCTS_ACTION, ignoreCase = true) -> onBrowseProducts()
            else -> vm.send(a, apiKey)
        }
    }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Automatyczne wysłanie wiadomości/zdjęcia + reset po „Wyczyść rozmowę" (z Profilu).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (app.pendingChatClear) { app.pendingChatClear = false; vm.resetInMemory() }
        app.pendingChatMessage?.let { msg -> app.pendingChatMessage = null; vm.send(msg, apiKey) }
        app.pendingChatPhoto?.let { b64 -> app.pendingChatPhoto = null; vm.sendPhoto(b64, apiKey) }
    }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = photoUri
        if (success && uri != null) vm.sendPhoto(ImageUtil.persistChatImage(context, uri), apiKey)
    }

    Column(modifier.fillMaxSize().background(Palette.Bg).imePadding().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Box(
                Modifier.size(40.dp).background(Palette.Green, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("🌱", fontSize = 20.sp) }
            Column(Modifier.padding(start = 10.dp)) {
                Text("Dietetyk AI", color = Palette.TextDark, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("● online", color = Palette.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Pasek postępu wywiadu strukturalnego — tylko dopóki nie ma jeszcze planu (etap onboardingu).
        var interview by remember { mutableStateOf<pl.filebit.dietetyk.ui.InterviewStep?>(null) }
        androidx.compose.runtime.LaunchedEffect(vm.messages.size) {
            val hasPlan = app.database.planDao().get() != null
            interview = if (hasPlan) null
            else pl.filebit.dietetyk.ui.interviewStep(app.profileRepo.get(), app.weightRepo.latest()?.weightKg)
        }
        interview?.let { iv ->
            androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Wywiad · ${iv.label}", color = Palette.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${iv.step}/${iv.total}", color = Palette.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).background(Palette.Line, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(iv.step.toFloat() / iv.total).height(6.dp).background(Palette.Green, RoundedCornerShape(3.dp)))
                }
            }
        }

        // Czat zawsze na dole: zjedź do najnowszej przy wejściu i przy każdej nowej wiadomości/„pisze…".
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(vm.messages.size, vm.sending) {
            val count = vm.messages.size + if (vm.sending) 1 else 0
            if (count > 0) listState.scrollToItem(count - 1)
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.messages) { msg -> MessageItem(msg, vm.sending, onAction) }
            if (vm.sending) item { Text("Dietetyk pisze…", color = Palette.Green, fontSize = 13.sp) }
        }
        // Podpowiedzi startowe — pokazują, co dietetyk potrafi (tylko w aktywnym użyciu, nie w wywiadzie,
        // gdy nie ma podsuniętych akcji i nie trwa odpowiedź). Rozwiązuje „pusty czat onieśmiela".
        val lastMsg = vm.messages.lastOrNull()
        val showStarters = interview == null && !vm.sending && lastMsg != null && !lastMsg.fromUser && lastMsg.actions.isEmpty() && lastMsg.cards.isEmpty()
        if (showStarters) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Co dziś zjeść?", "Zamień posiłek", "Jak mi idzie?", "Jestem na mieście").forEach { s ->
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.GreenTint, RoundedCornerShape(16.dp)).clickable { vm.send(s, apiKey) }.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) { Text(s, color = Palette.GreenDark, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        // Deterministyczny pasek pickera smaku — GWARANCJA zbiórki preferencji w onboardingu, niezależna od
        // tego, czy AI wyemituje akcję (prompt-only zawodził — patrz v1.16). Znika po przejściu pickera.
        if (interview != null && !app.settings.tastePickerSeen) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp))
                    .background(Palette.GreenTint, RoundedCornerShape(16.dp)).clickable { onOpenTastePicker() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("❤️", fontSize = 20.sp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("Zaznacz co lubisz i czego nie jesz", color = Palette.GreenDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Ułożę plan z Twoich produktów — zajmie 30 sekund", color = Palette.Muted, fontSize = 12.sp)
                }
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Green, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Otwórz", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.padding(end = 6.dp).clip(RoundedCornerShape(14.dp)).background(Palette.GreenTint, RoundedCornerShape(14.dp))
                    .clickable(enabled = !vm.sending) {
                        val uri = ImageUtil.newPhotoUri(context)
                        photoUri = uri
                        cameraLauncher.launch(uri)
                    }.padding(12.dp),
                contentAlignment = Alignment.Center
            ) { Text("📷", fontSize = 20.sp) }
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Napisz do dietetyka…") }, enabled = !vm.sending,
                shape = RoundedCornerShape(20.dp)
            )
            Button(
                onClick = { vm.send(input, apiKey); input = "" },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Green),
                modifier = Modifier.padding(start = 8.dp)
            ) { Text("Wyślij") }
        }
    }
}

private suspend fun sendToDietitian(
    app: DietetykApp, history: MutableList<JsonObject>, userText: String,
    handler: DietToolHandler, apiKey: String, imageB64: String? = null
): String {
    val ctx = app.contextBuilder.build(System.currentTimeMillis())
    val contextText = ctx?.let { DietitianPrompt.renderContext(it) }
        ?: DietitianPrompt.renderCareGuidance(CareState(CareStage.INTERVIEW, InterviewTopic.entries.toList()))
    val toneLine = when (app.settings.aiTone) {
        "gentle" -> "TON ROZMOWY: Bądź wyjątkowo ciepły, wspierający i wyrozumiały. Chwal nawet małe kroki, nigdy nie oceniaj ani nie strasz."
        "tough" -> "TON ROZMOWY: Bądź bezpośredni i wymagający — mów wprost, motywuj do dyscypliny i konsekwencji, bez owijania w bawełnę (ale z szacunkiem)."
        else -> "TON ROZMOWY: Bądź wyważony — konkretny i rzeczowy, ale ciepły i motywujący."
    }
    // OVERRIDE kontekstowy (ochrona zaufania): ustawiony ton to BAZA. DWIE OSIE, nie jedna:
    // (1) EMPATIA WOBEC CZŁOWIEKA jest bezwarunkowa — gdy user na dnie, nigdy nie dobijaj, nie oskarżaj.
    // (2) SZCZEROŚĆ WOBEC TRAJEKTORII jest warunkowa — gdy wzorzec się POWTARZA, nazwij go łagodnie i ZAPYTAJ
    //     co pod spodem. Szczerość ZAWSZE jako pytanie, nigdy werdykt (crisis-safe: pytanie otwiera drzwi
    //     i przy potknięciu, i przy realnym kryzysie). Rozgrzeszanie w kółko = nie troska, lecz obojętność.
    val toneOverride = "WAZNE: powyzszy ton to punkt wyjscia, nie sztywna regula. Czytaj stan uzytkownika. " +
        "EMPATIA WOBEC CZLOWIEKA JEST BEZWARUNKOWA — jesli jest zniechecony, ma gorszy dzien albo przyznaje sie do " +
        "potkniecia, ZAWSZE odpowiedz z empatia i wsparciem, nawet przy tonie wymagajacym; nigdy nie dobijaj kogos, " +
        "kto jest na dnie, i nigdy nie oskarzaj. ALE badz tez SZCZERY WOBEC TRAJEKTORII: gdy widzisz POWTARZAJACY SIE " +
        "wzorzec (kolejny tydzien bez progresu, nawracajace odkladanie celu, ta sama przeszkoda wracajaca w kolko), " +
        "nie udawaj ze wszystko sie uklada — nazwij trajektorie lagodnie i ZADAJ PYTANIE, co naprawde stoi pod spodem. " +
        "Szczerosc ZAWSZE w formie PYTANIA, nigdy werdyktu czy diagnozy: 'co ci realnie przeszkadza, ze od kilku tygodni " +
        "stoimy w miejscu?' zamiast 'oszukujesz sam siebie'. Pytanie otwiera drzwi zarowno gdy to chwilowe potkniecie, " +
        "jak i gdy user potrzebuje powazniejszej pomocy. Rozgrzeszanie w kolko to nie troska — to obojetnosc."
    val system = DietitianPrompt.systemPrompt() + "\n\n" + toneLine + "\n" + toneOverride + "\n\n" + contextText
    return DietitianConversation(ClaudeHttpApi(apiKey)).send(system, history, userText, handler, imageB64 = imageB64)
}

@Composable
private fun MessageItem(msg: UiMsg, sending: Boolean, onAction: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        msg.imageUri?.let { PhotoThumb(it, msg.fromUser) }
        if (msg.text.isNotBlank()) Bubble(msg)
        msg.cards.forEach { card -> ActionCard(card, onAction) }
        if (!msg.fromUser && msg.actions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                msg.actions.take(3).forEach { a ->
                    Box(
                        Modifier.weight(1f, fill = false).background(Palette.Green, RoundedCornerShape(20.dp))
                            .clickable(enabled = !sending) { onAction(a) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(a, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumb(path: String, fromUser: Boolean) {
    // Dekodowanie raz per ścieżka (inSampleSize chroni przed OOM). Null = plik nie istnieje
    // (np. po imporcie kopii z innego telefonu) → nie renderuj, tekst „📷 Zdjęcie posiłku" zostaje.
    val bmp = remember(path) { pl.filebit.dietetyk.ImageUtil.decodeThumb(path) } ?: return
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Zdjęcie posiłku",
            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
            modifier = Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(15.dp))
        )
    }
}

@Composable
private fun Bubble(msg: UiMsg) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (msg.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        // SelectionContainer = można zaznaczyć i skopiować tekst (długie przytrzymanie).
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(
                msg.text,
                color = if (msg.fromUser) Color.White else Palette.TextDark,
                fontSize = 15.sp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(if (msg.fromUser) Palette.Green else Palette.GreenTint, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ApiKeyGate(onSaved: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Palette.Bg).systemBarsPadding().imePadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Podaj klucz Claude API", color = Palette.TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Klucz zostaje na Twoim telefonie.", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("sk-ant-…") }, shape = RoundedCornerShape(12.dp))
        Button(
            onClick = { if (key.isNotBlank()) onSaved(key.trim()) },
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Green),
            modifier = Modifier.padding(top = 12.dp)
        ) { Text("Zapisz i zacznij") }
    }
}
