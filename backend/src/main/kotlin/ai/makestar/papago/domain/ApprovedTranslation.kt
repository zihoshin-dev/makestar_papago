package ai.makestar.papago.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "approved_translation",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_approved_hash_lang",
            columnNames = ["sourceTextHash", "targetLang"]
        )
    ],
    indexes = [
        Index(name = "idx_approved_hash_lang", columnList = "sourceTextHash,targetLang")
    ]
)
class ApprovedTranslation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 64)
    val sourceTextHash: String,

    @Column(nullable = false, length = 10)
    val targetLang: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    val sourceText: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    var approvedText: String,

    var usageCount: Int = 0,

    var approvalCount: Int = 1,

    var qualityScore: Double = 0.0,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    var updatedAt: LocalDateTime = LocalDateTime.now()
)
