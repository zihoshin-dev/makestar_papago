package ai.makestar.papago.service

import ai.makestar.papago.domain.Glossary
import ai.makestar.papago.domain.GlossaryMultiLangTokenRepository
import ai.makestar.papago.domain.GlossaryRepository
import ai.makestar.papago.domain.GlossaryTokenRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Value
import java.io.File

@Service
class GlossaryInitializer(
    private val glossaryRepository: GlossaryRepository,
    private val glossaryTokenRepository: GlossaryTokenRepository,
    private val multiLangTokenRepository: GlossaryMultiLangTokenRepository,
    private val tokenIndexService: TokenIndexService,
    private val objectMapper: ObjectMapper,
    @Value("\${glossary.local.path:}") private val localJsonPath: String
) {
    private val logger = LoggerFactory.getLogger(GlossaryInitializer::class.java)

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
        logger.info("Initialized ${glossaries.size} glossary items.")
    }

    @Async
    @EventListener(ApplicationReadyEvent::class)
    fun buildTokenIndexesAsync() {
        val glossaries = glossaryRepository.findAll()
        if (glossaries.isEmpty()) return

        // Build Korean token index
        if (glossaryTokenRepository.count() == 0L) {
            logger.info("Building Korean token index asynchronously...")
            tokenIndexService.buildTokenIndex(glossaries)
        }

        // Build multi-language token index
        if (multiLangTokenRepository.count() == 0L) {
            logger.info("Building multi-language token index asynchronously...")
            tokenIndexService.buildMultiLangIndex(glossaries)
        }
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
        if (localJsonPath.isNotBlank()) {
            val localFile = File(localJsonPath)
            if (localFile.exists()) {
                return localFile.readText()
            }
        }

        logger.warn("sheet_db.json not found in classpath or local path.")
        return null
    }

    private fun JsonNode.safeText(index: Int): String {
        return if (this.size() > index) this.get(index)?.asText() ?: "" else ""
    }
}
