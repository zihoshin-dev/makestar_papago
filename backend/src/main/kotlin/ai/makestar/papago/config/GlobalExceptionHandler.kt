package ai.makestar.papago.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    data class ErrorResponse(val error: String, val message: String)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Bad request: ${e.message}")
        return ResponseEntity.badRequest().body(ErrorResponse("BAD_REQUEST", e.message ?: "잘못된 요청이에요."))
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException): ResponseEntity<ErrorResponse> {
        logger.warn("Not found: ${e.message}")
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("NOT_FOUND", e.message ?: "요청한 항목을 찾을 수 없어요."))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(ErrorResponse("VALIDATION_ERROR", message))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("INTERNAL_ERROR", "서버에서 오류가 발생했어요. 잠시 후 다시 시도해 주세요."))
    }
}
