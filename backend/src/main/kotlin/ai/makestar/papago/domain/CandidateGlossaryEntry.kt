package ai.makestar.papago.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "candidate_glossary_entry",
    indexes = [
        Index(name = "idx_candidate_status", columnList = "status"),
        Index(name = "idx_candidate_source_ko", columnList = "sourceKo"),
        Index(name = "idx_candidate_confidence", columnList = "confidenceScore")
    ]
)
class CandidateGlossaryEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    val sourceKo: String,

    @Column(nullable = false, length = 10)
    val targetLang: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val proposedTranslation: String,

    @Column(nullable = false)
    var confidenceScore: Double = 0.3,

    @Column(columnDefinition = "TEXT")
    val context: String = "",

    @Column(length = 255)
    val pageUrl: String = "",

    var occurrenceCount: Int = 1,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: CandidateStatus = CandidateStatus.PENDING,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class CandidateStatus {
    PENDING,
    APPROVED,
    REJECTED,
    MERGED
}
