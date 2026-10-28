package com.example.adventurebook.exception;

import com.example.adventurebook.dto.ApiErrorDTO;

import jakarta.servlet.http.HttpServletRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiErrorDTO> notFound(
        NoSuchElementException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ApiErrorDTO> badRequest(Exception exception, HttpServletRequest request) {
        return response(
            HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidBookException.class)
    ResponseEntity<ApiErrorDTO> invalidBook(
        InvalidBookException exception, HttpServletRequest request) {
        List<String> details =
            exception.getIssues().stream()
                .map(issue -> issue.getCode() + ": " + issue.getMessage())
                .toList();

        return response(
            HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BOOK", exception.getMessage(), request, details);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorDTO> tooLarge(
        MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return response(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "FILE_TOO_LARGE",
            "Book exceeds the 1 MB limit",
            request,
            List.of());
    }

    @ExceptionHandler(RevisionConflictException.class)
    ResponseEntity<?> revision(RevisionConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getCurrent());
    }

    @ExceptionHandler(VersionConflictException.class)
    ResponseEntity<ApiErrorDTO> version(
        VersionConflictException exception, HttpServletRequest request) {
        return response(
            HttpStatus.CONFLICT, "BOOK_VERSION_CONFLICT", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiErrorDTO> state(IllegalStateException exception, HttpServletRequest request) {
        return response(
            HttpStatus.CONFLICT, "GAME_FINISHED", exception.getMessage(), request, List.of());
    }

    private static ResponseEntity<ApiErrorDTO> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request,
        List<String> details) {
        ApiErrorDTO error =
            new ApiErrorDTO(
                code, message, status.value(), request.getRequestURI(), OffsetDateTime.now(), details);

        return ResponseEntity.status(status).body(error);
    }
}
