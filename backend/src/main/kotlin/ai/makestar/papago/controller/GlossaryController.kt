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
