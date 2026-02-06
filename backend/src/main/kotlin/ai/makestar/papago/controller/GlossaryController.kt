package ai.makestar.papago.controller

import ai.makestar.papago.domain.Glossary
import ai.makestar.papago.domain.GlossaryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.*

data class GlossaryResponse(
    val id: Long,
    val pageUrl: String,
    val ko: String,
    val en: String,
    val ja: String,
    val zhHans: String,
    val zhHant: String,
    val es: String,
    val de: String,
    val fr: String
)

data class GlossaryPage(
    val content: List<GlossaryResponse>,
    val totalPages: Int,
    val totalElements: Long,
    val number: Int,
    val size: Int
)

data class GlossaryUpdateRequest(
    val ko: String? = null,
    val en: String? = null,
    val ja: String? = null,
    val zhHans: String? = null,
    val zhHant: String? = null,
    val es: String? = null,
    val de: String? = null,
    val fr: String? = null
)

data class GlossaryCreateRequest(
    val ko: String,
    val pageUrl: String = "/custom",
    val en: String = "",
    val ja: String = "",
    val zhHans: String = "",
    val zhHant: String = "",
    val es: String = "",
    val de: String = "",
    val fr: String = ""
)

@RestController
@RequestMapping("/api/glossary")
class GlossaryController(
    private val glossaryRepository: GlossaryRepository
) {
    @GetMapping
    fun getGlossary(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) pageUrl: String?
    ): GlossaryPage {
        val pageRequest = PageRequest.of(page, size.coerceAtMost(100), Sort.by("id"))

        val glossaryPage: Page<Glossary> = when {
            !search.isNullOrBlank() && !pageUrl.isNullOrBlank() -> {
                glossaryRepository.findByKoContainingAndPageUrl(search, pageUrl, pageRequest)
            }
            !search.isNullOrBlank() -> {
                glossaryRepository.findByKoContaining(search, pageRequest)
            }
            !pageUrl.isNullOrBlank() -> {
                glossaryRepository.findByPageUrl(pageUrl, pageRequest)
            }
            else -> {
                glossaryRepository.findAll(pageRequest)
            }
        }

        return GlossaryPage(
            content = glossaryPage.content.map { it.toResponse() },
            totalPages = glossaryPage.totalPages,
            totalElements = glossaryPage.totalElements,
            number = glossaryPage.number,
            size = glossaryPage.size
        )
    }

    @PostMapping
    fun createGlossary(@RequestBody request: GlossaryCreateRequest): GlossaryResponse {
        val keyName = request.ko.lowercase().replace(Regex("[^a-zA-Z0-9가-힣]"), "_")
        val glossary = Glossary(
            pageUrl = request.pageUrl,
            keyName = keyName,
            ko = request.ko,
            en = request.en,
            ja = request.ja,
            zhHans = request.zhHans,
            zhHant = request.zhHant,
            es = request.es,
            de = request.de,
            fr = request.fr
        )
        val saved = glossaryRepository.save(glossary)
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateGlossary(@PathVariable id: Long, @RequestBody request: GlossaryUpdateRequest): GlossaryResponse {
        val glossary = glossaryRepository.findById(id)
            .orElseThrow { RuntimeException("Glossary not found: $id") }

        request.ko?.let { glossary.ko = it }
        request.en?.let { glossary.en = it }
        request.ja?.let { glossary.ja = it }
        request.zhHans?.let { glossary.zhHans = it }
        request.zhHant?.let { glossary.zhHant = it }
        request.es?.let { glossary.es = it }
        request.de?.let { glossary.de = it }
        request.fr?.let { glossary.fr = it }

        val saved = glossaryRepository.save(glossary)
        return saved.toResponse()
    }

    private fun Glossary.toResponse() = GlossaryResponse(
        id = id ?: 0L,
        pageUrl = pageUrl,
        ko = ko,
        en = en,
        ja = ja,
        zhHans = zhHans,
        zhHant = zhHant,
        es = es,
        de = de,
        fr = fr
    )
}
