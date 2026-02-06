package ai.makestar.papago.service

import ai.makestar.papago.domain.*
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.LocalDateTime

@Service
class FeedbackService(
    private val historyRepository: TranslationHistoryRepository,
    private val approvedRepository: ApprovedTranslationRepository
) {

    fun recordTranslation(
        sourceText: String,
        targetLang: String,
        translatedText: String,
        matchedGlossaryCount: Int,
        isFromCache: Boolean
    ): Long {
        val history = TranslationHistory(
            sourceText = sourceText,
            sourceTextHash = sha256(sourceText),
            targetLang = targetLang,
            translatedText = translatedText,
            matchedGlossaryCount = matchedGlossaryCount,
            isFromCache = isFromCache
        )
        return historyRepository.save(history).id!!
    }

    fun submitFeedback(
        historyId: Long,
        status: TranslationStatus,
        rating: Int?,
        correctedText: String?
    ) {
        val history = historyRepository.findById(historyId)
            .orElseThrow { IllegalArgumentException("History not found: $historyId") }

        history.status = status
        history.rating = rating
        history.reviewedAt = LocalDateTime.now()

        if (status == TranslationStatus.CORRECTED && !correctedText.isNullOrBlank()) {
            history.userCorrectedText = correctedText
        }

        historyRepository.save(history)

        // If approved or corrected, upsert into ApprovedTranslation
        if (status == TranslationStatus.APPROVED || status == TranslationStatus.CORRECTED) {
            val approvedText = if (status == TranslationStatus.CORRECTED && !correctedText.isNullOrBlank()) {
                correctedText
            } else {
                history.translatedText
            }
            upsertApprovedTranslation(history.sourceText, history.sourceTextHash, history.targetLang, approvedText)
        }
    }

    fun findApprovedTranslation(sourceText: String, targetLang: String): ApprovedTranslation? {
        val hash = sha256(sourceText)
        val approved = approvedRepository.findBySourceTextHashAndTargetLang(hash, targetLang)
        if (approved != null) {
            approved.usageCount++
            approvedRepository.save(approved)
        }
        return approved
    }

    private fun upsertApprovedTranslation(
        sourceText: String,
        sourceTextHash: String,
        targetLang: String,
        approvedText: String
    ) {
        val existing = approvedRepository.findBySourceTextHashAndTargetLang(sourceTextHash, targetLang)
        if (existing != null) {
            existing.approvedText = approvedText
            existing.approvalCount++
            existing.updatedAt = LocalDateTime.now()
            approvedRepository.save(existing)
        } else {
            approvedRepository.save(
                ApprovedTranslation(
                    sourceTextHash = sourceTextHash,
                    targetLang = targetLang,
                    sourceText = sourceText,
                    approvedText = approvedText
                )
            )
        }
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
