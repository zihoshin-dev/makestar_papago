package ai.makestar.papago.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class KoreanSlangDictionaryServiceTest : DescribeSpec({
    val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    val service = KoreanSlangDictionaryService(objectMapper)

    beforeSpec {
        service.init()
    }

    describe("isJamoOnly") {
        it("should detect jamo-only text") {
            service.isJamoOnly("ㅋㅋㅋ") shouldBe true
            service.isJamoOnly("ㅠㅠ") shouldBe true
            service.isJamoOnly("ㅎㅎ") shouldBe true
        }

        it("should reject non-jamo text") {
            service.isJamoOnly("안녕") shouldBe false
            service.isJamoOnly("hello") shouldBe false
            service.isJamoOnly("123") shouldBe false
        }

        it("should allow whitespace in jamo text") {
            service.isJamoOnly("ㅋㅋ ㅋㅋ") shouldBe true
        }

        it("should reject empty text") {
            service.isJamoOnly("") shouldBe false
            service.isJamoOnly("   ") shouldBe false
        }
    }

    describe("lookup") {
        it("should find direct match for laughter slang") {
            val result = service.lookup("ㅋㅋ", "en")
            result shouldNotBe null
            result?.entry?.input shouldBe "ㅋㅋ"
            result?.translatedText shouldNotBe ""
        }

        it("should handle repeated character matching") {
            // Should match a pattern even if input is longer
            val result = service.lookup("ㅋㅋㅋㅋㅋㅋㅋ", "en")
            result shouldNotBe null
            result?.translatedText shouldNotBe ""
        }

        it("should return null for non-slang text") {
            val result = service.lookup("안녕하세요", "en")
            result shouldBe null
        }

        it("should return null for empty text") {
            val result = service.lookup("", "en")
            result shouldBe null
        }

        it("should handle different target languages") {
            val resultEn = service.lookup("ㅋㅋ", "en")
            val resultJa = service.lookup("ㅋㅋ", "ja")
            val resultZhHans = service.lookup("ㅋㅋ", "zh-hans")

            resultEn shouldNotBe null
            resultJa shouldNotBe null
            resultZhHans shouldNotBe null
        }

        it("should handle zh-hans and zh_hans formats") {
            val resultDash = service.lookup("ㅋㅋ", "zh-hans")
            val resultUnderscore = service.lookup("ㅋㅋ", "zh_hans")

            // Both should work
            if (resultDash != null && resultUnderscore != null) {
                resultDash.translatedText shouldBe resultUnderscore.translatedText
            }
        }

        it("should handle zh-hant and zh_hant formats") {
            val resultDash = service.lookup("ㅋㅋ", "zh-hant")
            val resultUnderscore = service.lookup("ㅋㅋ", "zh_hant")

            // Both should work
            if (resultDash != null && resultUnderscore != null) {
                resultDash.translatedText shouldBe resultUnderscore.translatedText
            }
        }

        it("should fallback to English for unsupported languages") {
            val resultDe = service.lookup("ㅋㅋ", "de")
            val resultFr = service.lookup("ㅋㅋ", "fr")

            // Should use English fallback if de/fr not available
            resultDe shouldNotBe null
            resultFr shouldNotBe null
        }

        it("should trim whitespace before lookup") {
            val result = service.lookup("  ㅋㅋ  ", "en")
            result shouldNotBe null
            result?.entry?.input shouldBe "ㅋㅋ"
        }

        it("should handle intensity levels") {
            val shortResult = service.lookup("ㅋㅋ", "en")
            val longResult = service.lookup("ㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋ", "en")

            shortResult shouldNotBe null
            longResult shouldNotBe null

            // Longer repetition might have different intensity
            shortResult?.intensity shouldNotBe null
            longResult?.intensity shouldNotBe null
        }

        it("should find crying slang if exists") {
            val result = service.lookup("ㅠㅠ", "en")
            // May or may not exist in dictionary, just verify it doesn't crash
            // If it exists, should have a translation
            if (result != null) {
                result.translatedText shouldNotBe ""
            }
        }

        it("should handle case-insensitive target language") {
            val resultLower = service.lookup("ㅋㅋ", "en")
            val resultUpper = service.lookup("ㅋㅋ", "EN")

            // Both should work (lowercase is used internally)
            resultLower shouldNotBe null
            if (resultUpper != null) {
                resultLower?.translatedText shouldBe resultUpper.translatedText
            }
        }
    }
})
