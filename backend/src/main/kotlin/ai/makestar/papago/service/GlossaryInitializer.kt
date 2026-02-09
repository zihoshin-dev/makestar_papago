package ai.makestar.papago.service

import ai.makestar.papago.domain.Glossary
import ai.makestar.papago.domain.GlossaryRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.File

@Service
class GlossaryInitializer(
    private val glossaryRepository: GlossaryRepository,
    private val tokenIndexService: TokenIndexService,
    private val objectMapper: ObjectMapper
) {
    private val localJsonPath = "/Users/zihoshin/clawd/data-collection/sheet_db.json"

    @PostConstruct
    fun init() {
        if (glossaryRepository.count() > 0) return

        val jsonContent = loadJsonContent() ?: return
        val root: JsonNode = objectMapper.readTree(jsonContent)
        val values = root.get("values") ?: return

        val glossaries = mutableListOf<Glossary>()

        // Row 0-1 are headers; data starts at index 2
        for (i in 2 until values.size()) {
            val row = values.get(i)
            if (row.size() < 6) continue

            glossaries.add(
                Glossary(
                    pageUrl = row.safeText(0),
                    keyName = row.safeText(1),
                    ko = row.safeText(2),
                    en = row.safeText(3),
                    zhHans = row.safeText(4),
                    ja = row.safeText(5),
                    es = row.safeText(6),
                    zhHant = row.safeText(7),
                    // DeepL variants
                    zhHantFromEn = row.safeText(8),
                    zhHantFromKo = row.safeText(9),
                    zhHansFromEn = row.safeText(10),
                    zhHansFromKo = row.safeText(11),
                    // Business team corrections
                    zhHantTaiwan = row.safeText(12),
                    zhHansChina = row.safeText(13),
                    enNorthAmerica = row.safeText(14),
                    jaJapan = row.safeText(15),
                    // Additional languages
                    de = row.safeText(16),
                    fr = row.safeText(17)
                )
            )
        }

        glossaryRepository.saveAll(glossaries)
        println("Initialized ${glossaries.size} glossary items.")

        // Build token index
        tokenIndexService.buildTokenIndex(glossaries)

        // Build multi-language token index
        tokenIndexService.buildMultiLangIndex(glossaries)
    }

    private fun loadJsonContent(): String? {
        // 1. Try classpath resource first (works in Docker/JAR)
        try {
            val resource = ClassPathResource("data/sheet_db.json")
            if (resource.exists()) {
                return resource.inputStream.bufferedReader().readText()
            }
        } catch (_: Exception) { }

        // 2. Fallback to local file path (development only)
        val localFile = File(localJsonPath)
        if (localFile.exists()) {
            return localFile.readText()
        }

        println("WARNING: sheet_db.json not found in classpath or local path.")
        return null
    }

    private fun JsonNode.safeText(index: Int): String {
        return if (this.size() > index) this.get(index)?.asText() ?: "" else ""
    }
}
