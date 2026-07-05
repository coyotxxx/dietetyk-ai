package pl.filebit.dietetyk

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
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
    val cards: List<pl.filebit.dietetyk.ui.CardData> = emptyList()
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
            }
        }
    }

    private fun ChatMessageEntity.toUiMsg(): UiMsg {
        val cards = runCatching {
            if (cardsJson.isBlank()) emptyList()
            else kotlinx.serialization.json.Json.parseToJsonElement(cardsJson).jsonArray.mapNotNull { it as? JsonObject }
                .map { pl.filebit.dietetyk.ui.CardData(it["type"]?.jsonPrimitive?.content ?: "generic", it) }
        }.getOrDefault(emptyList())
        return UiMsg(fromUser, text, if (actionsCsv.isBlank()) emptyList() else actionsCsv.split("|"), cards)
    }

    private suspend fun persist(msg: UiMsg) {
        val cardsJson = if (msg.cards.isEmpty()) "" else buildJsonArray { msg.cards.forEach { add(it.json) } }.toString()
        app.database.chatMessageDao().insert(
            ChatMessageEntity(fromUser = msg.fromUser, text = msg.text, actionsCsv = msg.actions.joinToString("|"), cardsJson = cardsJson, createdAt = System.currentTimeMillis())
        )
    }

    private fun saveHistory() { app.settings.chatHistoryJson = buildJsonArray { history.forEach { add(it) } }.toString() }

    fun send(raw: String, apiKey: String) {
        val text = raw.trim()
        if (text.isEmpty() || sending) return
        val userMsg = UiMsg(true, text); messages.add(userMsg); sending = true
        viewModelScope.launch {
            persist(userMsg)
            val reply = runCatching { sendToDietitian(app, history, text, handler, apiKey) }.getOrElse { "Coś poszło nie tak: ${it.message}" }
            val aiMsg = parseAiReply(reply); messages.add(aiMsg); persist(aiMsg); saveHistory()
            sending = false
        }
    }

    fun sendPhoto(b64: String?, apiKey: String) {
        if (sending) return
        val userMsg = UiMsg(true, "📷 Zdjęcie posiłku"); messages.add(userMsg); sending = true
        viewModelScope.launch {
            persist(userMsg)
            val reply = if (b64 == null) "Nie udało się odczytać zdjęcia — spróbuj jeszcze raz."
            else runCatching {
                sendToDietitian(app, history, "To zdjęcie mojego posiłku. Rozpoznaj co to, oszacuj kalorie i makro, i zapytaj czy zapisać.", handler, apiKey, imageB64 = b64)
            }.getOrElse { "Coś poszło nie tak przy analizie zdjęcia: ${it.message}" }
            val aiMsg = parseAiReply(reply); messages.add(aiMsg); persist(aiMsg); saveHistory()
            sending = false
        }
    }
}

@Composable
fun ChatScreen(app: DietetykApp, modifier: Modifier = Modifier) {
    var apiKey by remember { mutableStateOf(app.settings.apiKey) }
    if (apiKey.isBlank()) {
        ApiKeyGate(onSaved = { app.settings.apiKey = it; apiKey = it })
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: ChatViewModel = viewModel { ChatViewModel(app) }
    var input by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Automatyczne wysłanie wiadomości ustawionej z innego ekranu (np. „Zacznij wizytę").
    androidx.compose.runtime.LaunchedEffect(Unit) {
        app.pendingChatMessage?.let { msg -> app.pendingChatMessage = null; vm.send(msg, apiKey) }
    }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = photoUri
        if (success && uri != null) vm.sendPhoto(ImageUtil.toBase64Jpeg(context, uri), apiKey)
    }

    Column(modifier.fillMaxSize().background(Palette.Bg).imePadding().padding(12.dp)) {
        Text("Dietetyk AI", color = Palette.TextDark, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.messages) { msg -> MessageItem(msg, vm.sending) { vm.send(it, apiKey) } }
            if (vm.sending) item { Text("Dietetyk pisze…", color = Palette.Green, fontSize = 13.sp) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.padding(end = 6.dp).background(Palette.GreenTint, RoundedCornerShape(14.dp))
                    .clickable(enabled = !vm.sending) {
                        val uri = ImageUtil.newPhotoUri(context)
                        photoUri = uri
                        cameraLauncher.launch(uri)
                    }.padding(12.dp),
                contentAlignment = Alignment.Center
            ) { Text("📷", fontSize = 20.sp) }
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Napisz do dietetyka…") }, enabled = !vm.sending
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
    val system = DietitianPrompt.systemPrompt() + "\n\n" + contextText
    return DietitianConversation(ClaudeHttpApi(apiKey)).send(system, history, userText, handler, imageB64 = imageB64)
}

@Composable
private fun MessageItem(msg: UiMsg, sending: Boolean, onAction: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
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
private fun Bubble(msg: UiMsg) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (msg.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
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

@Composable
private fun ApiKeyGate(onSaved: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Palette.Bg).systemBarsPadding().imePadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Podaj klucz Claude API", color = Palette.TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Klucz zostaje na Twoim telefonie.", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("sk-ant-…") })
        Button(
            onClick = { if (key.isNotBlank()) onSaved(key.trim()) },
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Green),
            modifier = Modifier.padding(top = 12.dp)
        ) { Text("Zapisz i zacznij") }
    }
}
