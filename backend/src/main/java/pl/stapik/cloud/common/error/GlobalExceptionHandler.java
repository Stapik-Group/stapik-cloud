package pl.stapik.cloud.common.error;

import org.jspecify.annotations.NonNull;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import pl.stapik.cloud.admin.TwoFactorStateException;
import pl.stapik.cloud.admin.TwoFactorVerificationException;
import pl.stapik.cloud.security.ratelimit.TooManyRequestsException;

import java.net.URI;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problemDetail.setTitle("Unauthorized");
        problemDetail.setType(URI.create("urn:error:auth:unauthorized"));
        return problemDetail;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problemDetail.setTitle("Forbidden");
        problemDetail.setType(URI.create("urn:error:auth:forbidden"));
        return problemDetail;
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problemDetail.setTitle("Too many requests");
        problemDetail.setType(URI.create("urn:error:rate-limit:too-many-requests"));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfter().toSeconds()))
                .body(problemDetail);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNoSuchElementException(NoSuchElementException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Not found");
        problemDetail.setType(URI.create("urn:error:not-found"));
        return problemDetail;
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicateResource(DuplicateResourceException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Duplicate resource");
        problem.setProperty("constraint", ex.getConstraintName());
        problem.setType(URI.create("urn:error:duplicate-resource"));
        return problem;
    }

    @ExceptionHandler(TwoFactorVerificationException.class)
    public ProblemDetail handleTwoFactorVerification(TwoFactorVerificationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Verification failed");
        problem.setType(URI.create("urn:error:auth:two-factor-verification"));
        return problem;
    }

    @ExceptionHandler(TwoFactorStateException.class)
    public ProblemDetail handleTwoFactorState(TwoFactorStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Two-factor state conflict");
        problem.setType(URI.create("urn:error:auth:two-factor-state"));
        return problem;
    }

    @Override
    protected ResponseEntity<@NonNull Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                status,
                details.isEmpty() ? "Invalid request content." : details
        );

        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("urn:error:validation:bad-request"));

        problemDetail.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, GlobalExceptionHandler::getDefaultMessage, (m1, m2) -> m1)));

        return ResponseEntity.status(status).headers(headers).body(problemDetail);
    }

    private static String getDefaultMessage(FieldError fieldError) {
        String defaultMessage = fieldError.getDefaultMessage();
        return (defaultMessage == null) ? "" : defaultMessage;
    }
}
