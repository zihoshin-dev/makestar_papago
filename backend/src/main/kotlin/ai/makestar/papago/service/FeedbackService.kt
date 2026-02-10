package ai.makestar.papago.service

import ai.makestar.papago.domain.*
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

    @Transactional
    @CacheEvict(cacheNames = ["approvedTranslations", "approvedExamples"], allEntries = true)
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

    @Cacheable(cacheNames = ["approvedTranslations"], key = "#sourceText + '_' + #targetLang")
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

    @Transactional
    fun updateVerificationStatus(
        historyId: Long,
        verificationStatus: String,
        verificationIssues: String?
    ) {
        val history = historyRepository.findById(historyId)
            .orElseThrow { IllegalArgumentException("History not found: $historyId") }

        history.verificationStatus = verificationStatus
        history.verificationIssues = verificationIssues
        history.verifiedAt = LocalDateTime.now()

        historyRepository.save(history)
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
