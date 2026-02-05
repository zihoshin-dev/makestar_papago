package ai.makestar.papago.service

import ai.makestar.papago.domain.Glossary
import ai.makestar.papago.domain.GlossaryRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.io.File

@Service
class GlossaryInitializer(
    private val glossaryRepository: GlossaryRepository,
    private val objectMapper: ObjectMapper
) {
    // clawd 워크스페이스의 데이터를 프로젝트로 복사하거나 직접 읽음
    private val jsonPath = "/Users/zihoshin/clawd/data-collection/sheet_db.json"

    @PostConstruct
    fun init() {
        if (glossaryRepository.count() > 0) return

        val file = File(jsonPath)
        if (!file.exists()) return

        val root: JsonNode = objectMapper.readTree(file)
        val values = root.get("values") ?: return

        val glossaries = mutableListOf<Glossary>()
        
        // 0, 1번 로우는 헤더이므로 2번 인덱스부터 시작
        for (i in 2 until values.size()) {
            val row = values.get(i)
            if (row.size() < 6) continue

            glossaries.add(
                Glossary(
                    pageUrl = row.get(0)?.asText() ?: "",
                    keyName = row.get(1)?.asText() ?: "",
                    ko = row.get(2)?.asText() ?: "",
                    en = row.get(3)?.asText() ?: "",
                    zhHans = row.get(4)?.asText() ?: "",
                    ja = row.get(5)?.asText() ?: "",
                    es = if (row.size() > 6) row.get(6)?.asText() ?: "" else "",
                    zhHant = if (row.size() > 7) row.get(7)?.asText() ?: "" else ""
                )
            )
        }
        
        glossaryRepository.saveAll(glossaries)
        println("Successfully initialized ${glossaries.size} glossary items.")
    }
}
