package pl.filebit.dietetyk.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pl.filebit.dietetyk.core.aicontract.AiToolCatalog
import pl.filebit.dietetyk.core.aicontract.AiToolParam
import pl.filebit.dietetyk.core.aicontract.AiToolSpec

/**
 * Tłumaczy kanoniczny [AiToolCatalog] (`:core-domain`) na format `tools` API Claude
 * (name + description + input_schema wg JSON Schema). Dzięki temu AI ma dostęp do wszystkich
 * narzędzi zdefiniowanych w rdzeniu — jedno źródło prawdy.
 */
object ClaudeToolMapper {

    fun toolsJson(specs: List<AiToolSpec> = AiToolCatalog.all): JsonArray = buildJsonArray {
        specs.forEach { spec ->
            add(buildJsonObject {
                put("name", spec.name)
                put("description", spec.description)
                put("input_schema", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        spec.params.forEach { p -> put(p.name, paramSchema(p)) }
                    })
                    put("required", buildJsonArray {
                        spec.params.filter { it.required }.forEach { add(it.name) }
                    })
                })
            })
        }
    }

    private fun paramSchema(p: AiToolParam) = buildJsonObject {
        put("type", jsonType(p.type))
        put("description", p.description)
    }

    /** Mapuje typy semantyczne katalogu na typy JSON Schema. */
    private fun jsonType(t: String): String = when (t) {
        "int" -> "integer"
        "double" -> "number"
        "bool" -> "boolean"
        "enum" -> "string"   // enum reprezentowany jako string (wartości w opisie)
        "object" -> "object"
        "array" -> "array"
        else -> "string"
    }
}
