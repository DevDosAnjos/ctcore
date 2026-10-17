package br.com.ctcore.api.error;

import br.com.ctcore.identity.IdentityException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ApiError.Field> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.Field(f.getField(), f.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, req, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed(HttpServletRequest req) {
        return build(ErrorCode.MALFORMED_REQUEST, req, List.of());
    }

    @ExceptionHandler(ApiValidationException.class)
    ResponseEntity<ApiError> invalidInput(ApiValidationException e, HttpServletRequest req) {
        return build(ErrorCode.VALIDATION_ERROR, req, e.fields);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missingParam(MissingServletRequestParameterException e, HttpServletRequest req) {
        return build(ErrorCode.VALIDATION_ERROR, req, List.of(new ApiError.Field(e.getParameterName(), "obrigatório")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> wrongType(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        return build(ErrorCode.VALIDATION_ERROR, req, List.of(new ApiError.Field(e.getName(), "valor inválido")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMedia(HttpServletRequest req) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, req, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthenticated(HttpServletRequest req) {
        return build(ErrorCode.UNAUTHENTICATED, req, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(HttpServletRequest req) {
        return build(ErrorCode.FORBIDDEN, req, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(HttpServletRequest req) {
        return build(ErrorCode.NOT_FOUND, req, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpServletRequest req) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, req, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("Data conflict", e);
        return build(ErrorCode.CONFLICT, req, List.of());
    }

    @ExceptionHandler(IdentityException.class)
    ResponseEntity<ApiError> identity(IdentityException e, HttpServletRequest req) {
        ErrorCode code = switch (e.code()) {
            case MINOR_HAS_NO_ACCOUNT -> ErrorCode.MINOR_HAS_NO_ACCOUNT;
            case INVITE_INVALID -> ErrorCode.INVITE_INVALID;
            case TOO_MANY_REQUESTS -> ErrorCode.TOO_MANY_REQUESTS;
            case INVALID_CREDENTIALS -> ErrorCode.INVALID_CREDENTIALS;
            case PASSWORD_WEAK -> ErrorCode.PASSWORD_WEAK;
            case PASSWORD_LEAKED -> ErrorCode.PASSWORD_LEAKED;
            case ALREADY_LINKED -> ErrorCode.CONFLICT;
            case CHALLENGE_FAILED -> ErrorCode.CHALLENGE_FAILED;
            case RESET_INVALID -> ErrorCode.RESET_LINK_INVALID;
            case LINK_NOT_FOUND -> ErrorCode.NOT_FOUND;
        };
        // Motivo (usado, vencido, cancelado) só para quem tem o código certo; convite desconhecido não diz nada.
        boolean reason = code == ErrorCode.INVITE_INVALID && List.of("USED", "EXPIRED", "CANCELED").contains(e.detail());
        return build(code, req, reason ? List.of(new ApiError.Field("reason", e.detail())) : List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
        log.error("Unexpected error", e);
        return build(ErrorCode.INTERNAL_ERROR, req, List.of());
    }

    private static ResponseEntity<ApiError> build(ErrorCode code, HttpServletRequest req, List<ApiError.Field> fields) {
        return ResponseEntity.status(code.status).body(ApiError.of(code, TraceIdFilter.of(req), fields));
    }
}
