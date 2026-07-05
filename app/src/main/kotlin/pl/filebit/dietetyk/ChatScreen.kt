package pl.filebit.dietetyk

import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import pl.filebit.dietetyk.ai.ClaudeHttpApi
import pl.filebit.dietetyk.ai.DietToolHandler
import pl.filebit.dietetyk.ai.DietitianConversation
import pl.filebit.dietetyk.core.aicontract.CareStage
import pl.filebit.dietetyk.core.aicontract.CareState
import pl.filebit.dietetyk.core.aicontract.DietitianPrompt
import pl.filebit.dietetyk.core.aicontract.InterviewTopic

private data class UiMsg(val fromUser: Boolean, val text: String)

private val Bg = Color(0xFFE9E2D6)
private val Green = Color(0xFF3E7C5B)
private val TextDark = Color(0xFF23261F)

@Composable
fun ChatScreen(app: DietetykApp) {
    var apiKey by remember { mutableStateOf(app.settings.apiKey) }
    if (apiKey.isBlank()) {
        ApiKeyGate(onSaved = { app.settings.apiKey = it; apiKey = it })
        return
    }

    val scope = rememberCoroutineScope()
    val messages = remember {
        listOf(UiMsg(false, "Cześć! Jestem Twoim dietetykiem. Opowiedz mi o sobie — co chciałbyś osiągnąć?")).toMutableStateList()
    }
    val history = remember { mutableListOf<JsonObject>() }
    val handler = remember { DietToolHandler(app) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding().padding(12.dp)) {
        Text("Dietetyk AI", color = TextDark, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { msg -> Bubble(msg) }
            if (sending) item { Text("Dietetyk pisze…", color = Green, fontSize = 13.sp) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Napisz do dietetyka…") }, enabled = !sending
            )
            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isEmpty() || sending) return@Button
                    input = ""
                    messages.add(UiMsg(true, text))
                    sending = true
                    scope.launch {
                        val reply = runCatching { sendToDietitian(app, history, text, handler, apiKey) }
                            .getOrElse { "Coś poszło nie tak: ${it.message}" }
                        messages.add(UiMsg(false, reply))
                        sending = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                modifier = Modifier.padding(start = 8.dp)
            ) { Text("Wyślij") }
        }
    }
}

private suspend fun sendToDietitian(
    app: DietetykApp, history: MutableList<JsonObject>, userText: String,
    handler: DietToolHandler, apiKey: String
): String {
    val ctx = app.contextBuilder.build(System.currentTimeMillis())
    val contextText = ctx?.let { DietitianPrompt.renderContext(it) }
        ?: DietitianPrompt.renderCareGuidance(CareState(CareStage.INTERVIEW, InterviewTopic.entries.toList()))
    val system = DietitianPrompt.systemPrompt() + "\n\n" + contextText
    return DietitianConversation(ClaudeHttpApi(apiKey)).send(system, history, userText, handler)
}

@Composable
private fun Bubble(msg: UiMsg) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (msg.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            msg.text,
            color = if (msg.fromUser) Color.White else TextDark,
            fontSize = 15.sp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(if (msg.fromUser) Green else Color(0xFFEAF3EC), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ApiKeyGate(onSaved: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Podaj klucz Claude API", color = TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Klucz zostaje na Twoim telefonie.", color = Color(0xFF7A857D), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("sk-ant-…") })
        Button(
            onClick = { if (key.isNotBlank()) onSaved(key.trim()) },
            colors = ButtonDefaults.buttonColors(containerColor = Green),
            modifier = Modifier.padding(top = 12.dp)
        ) { Text("Zapisz i zacznij") }
    }
}
