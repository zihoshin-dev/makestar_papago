package ai.makestar.papago.domain

import jakarta.persistence.*
import java.time.LocalDateTime

enum class TranslationStatus {
    PENDING, APPROVED, CORRECTED, REJECTED
}

@Entity
@Table(
    name = "translation_history",
    indexes = [
        Index(name = "idx_history_source_hash", columnList = "sourceTextHash"),
        Index(name = "idx_history_status", columnList = "status"),
        Index(name = "idx_history_created", columnList = "createdAt")
    ]
)
class TranslationHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(columnDefinition = "TEXT", nullable = false)
    val sourceText: String,

    @Column(nullable = false, length = 64)
    val sourceTextHash: String,

    @Column(nullable = false, length = 10)
    val targetLang: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    val translatedText: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TranslationStatus = TranslationStatus.PENDING,

    @Column(columnDefinition = "TEXT")
    var userCorrectedText: String? = null,

    var rating: Int? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    var reviewedAt: LocalDateTime? = null,

    val matchedGlossaryCount: Int = 0,

    val isFromCache: Boolean = false,

    @Column(columnDefinition = "TEXT")
    var verificationStatus: String? = null,

    @Column(columnDefinition = "TEXT")
    var verificationIssues: String? = null,

    var verifiedAt: LocalDateTime? = null
)
