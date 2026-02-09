package ai.makestar.papago.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class LanguageDetectionServiceTest : DescribeSpec({
    val service = LanguageDetectionService()

    describe("detectLanguage") {
        it("should detect Korean") {
            service.detectLanguage("안녕하세요") shouldBe "ko"
            service.detectLanguage("한국어 텍스트입니다") shouldBe "ko"
            service.detectLanguage("방탄소년단") shouldBe "ko"
        }

        it("should detect English") {
            service.detectLanguage("Hello world") shouldBe "en"
            service.detectLanguage("This is English text") shouldBe "en"
            service.detectLanguage("Testing language detection") shouldBe "en"
        }

        it("should detect Japanese") {
            service.detectLanguage("こんにちは") shouldBe "ja"
            service.detectLanguage("カタカナ") shouldBe "ja"
            service.detectLanguage("ひらがなカタカナ") shouldBe "ja"
        }

        it("should detect Chinese") {
            service.detectLanguage("你好世界") shouldBe "zh"
            service.detectLanguage("中文测试文本") shouldBe "zh"
        }

        it("should detect Spanish as English (fallback for Latin)") {
            service.detectLanguage("Hola mundo") shouldBe "en"
            service.detectLanguage("Buenos días") shouldBe "en"
        }

        it("should detect Korean with jamo characters") {
            service.detectLanguage("ㅋㅋㅋ") shouldBe "ko"
            service.detectLanguage("ㅠㅠ") shouldBe "ko"
        }

        it("should handle mixed text with dominant language") {
            service.detectLanguage("안녕하세요 Hello") shouldBe "ko"
            service.detectLanguage("Hello 안녕") shouldBe "en"
        }

        it("should handle text with numbers and punctuation") {
            service.detectLanguage("안녕하세요 123!!!") shouldBe "ko"
            service.detectLanguage("Hello 123 world!!!") shouldBe "en"
        }

        it("should default to English for empty text") {
            service.detectLanguage("") shouldBe "en"
        }

        it("should default to English for text with only whitespace") {
            service.detectLanguage("   ") shouldBe "en"
        }

        it("should default to English for text with only numbers and punctuation") {
            service.detectLanguage("123!!!") shouldBe "en"
        }

        it("should handle text with mixed CJK characters") {
            // Japanese has priority over Chinese when kana is present
            service.detectLanguage("こんにちは世界") shouldBe "ja"
            // Pure CJK detected as Chinese
            service.detectLanguage("世界你好") shouldBe "zh"
        }

        it("should handle accented Latin characters") {
            service.detectLanguage("Café résumé") shouldBe "en"
            service.detectLanguage("Español") shouldBe "en"
        }
    }
})
