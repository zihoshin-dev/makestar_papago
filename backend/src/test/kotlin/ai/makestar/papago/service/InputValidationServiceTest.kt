package ai.makestar.papago.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class InputValidationServiceTest : DescribeSpec({
    val service = InputValidationService()

    describe("validate") {
        it("should pass valid text") {
            val result = service.validate("Hello world")
            result.isValid shouldBe true
            result.reason shouldBe null
        }

        it("should pass valid Korean text") {
            val result = service.validate("안녕하세요")
            result.isValid shouldBe true
            result.reason shouldBe null
        }

        it("should reject empty text") {
            val result = service.validate("")
            result.isValid shouldBe false
            result.reason shouldBe "EMPTY"
        }

        it("should reject blank text") {
            val result = service.validate("   ")
            result.isValid shouldBe false
            result.reason shouldBe "EMPTY"
        }

        it("should reject text shorter than minimum length") {
            val result = service.validate("a")
            result.isValid shouldBe false
            result.reason shouldBe "TOO_SHORT"
        }

        it("should pass text at exactly 2 characters") {
            val result = service.validate("ab")
            result.isValid shouldBe true
        }

        it("should reject text longer than 3000 characters") {
            val longText = "a".repeat(3001)
            val result = service.validate(longText)
            result.isValid shouldBe false
            result.reason shouldBe "TOO_LONG"
        }

        it("should pass text at exactly 3000 characters") {
            val exactText = "a".repeat(3000)
            val result = service.validate(exactText)
            result.isValid shouldBe true
        }

        it("should reject text with only numbers") {
            val result = service.validate("123456")
            result.isValid shouldBe false
            result.reason shouldBe "NO_LETTERS"
        }

        it("should reject text with only symbols") {
            val result = service.validate("!@#$%^&*()")
            result.isValid shouldBe false
            result.reason shouldBe "NO_LETTERS"
        }

        it("should reject text with only whitespace and numbers") {
            val result = service.validate("123 456 789")
            result.isValid shouldBe false
            result.reason shouldBe "NO_LETTERS"
        }

        it("should pass text with letters and numbers") {
            val result = service.validate("hello123")
            result.isValid shouldBe true
        }

        it("should pass text with Korean jamo") {
            val result = service.validate("ㅋㅋㅋ")
            result.isValid shouldBe true
        }

        it("should pass text with whitespace trimmed") {
            val result = service.validate("  hello  ")
            result.isValid shouldBe true
        }
    }

    describe("getErrorMessage") {
        it("should return correct message for EMPTY") {
            service.getErrorMessage("EMPTY") shouldBe "텍스트를 입력해 주세요."
        }

        it("should return correct message for TOO_SHORT") {
            service.getErrorMessage("TOO_SHORT") shouldBe "번역할 수 있는 텍스트를 입력해 주세요."
        }

        it("should return correct message for TOO_LONG") {
            service.getErrorMessage("TOO_LONG") shouldBe "텍스트가 너무 길어요. 3000자 이하로 입력해 주세요."
        }

        it("should return correct message for NO_LETTERS") {
            service.getErrorMessage("NO_LETTERS") shouldBe "번역할 수 있는 텍스트를 입력해 주세요."
        }

        it("should return default message for unknown reason") {
            service.getErrorMessage("UNKNOWN") shouldBe "번역할 수 없는 텍스트예요."
        }
    }
})
