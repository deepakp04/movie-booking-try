package com.moviebooking.common.exception;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.moviebooking.common.response.ApiResponse;
import com.moviebooking.common.response.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex) {

        ErrorResponse response =
                new ErrorResponse(
                        false,
                        ex.getMessage(),
                        List.of()
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex) {

        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        ErrorResponse response =
                new ErrorResponse(
                        false,
                        "Something went wrong",
                        List.of(ex.getMessage())
                );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField() + ": " + error.getDefaultMessage())
                .toList();

        ErrorResponse response =
                new ErrorResponse(
                        false,
                        "Validation failed",
                        errors
                );

        return ResponseEntity
                .badRequest()
                .body(response);
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse response = new ErrorResponse(false, ex.getMessage(), List.of());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // Spring's own static-resource miss (e.g. /favicon.ico). Semantically a 404,
    // not a 500 — the catch-all below would otherwise log a full stack trace
    // for every missing icon request.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Static resource not found: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(false, "Resource not found", List.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // A body that cannot be bound (an unsupported value for an enum field such as
    // cbfcRating, or malformed JSON) is the client's mistake, not a server
    // failure, and the reason is worth showing. This previously fell through to
    // the catch-all and reached the portals as an opaque 500 "Something went
    // wrong", which is precisely what made a mistyped movie format look like a
    // crash instead of an explainable error.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        String message = describeUnreadableBody(ex);
        log.warn("Rejected request body: {}", message);
        ErrorResponse response = new ErrorResponse(false, message, List.of());
        return ResponseEntity.badRequest().body(response);
    }

    private String describeUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();

        if (cause instanceof InvalidFormatException invalid) {
            Class<?> target = invalid.getTargetType();
            String field = lastFieldName(invalid);
            String value = String.valueOf(invalid.getValue());

            if (target != null && target.isEnum()) {
                String accepted = Arrays.stream(target.getEnumConstants())
                        .map(String::valueOf)
                        .collect(Collectors.joining(", "));
                return (field != null ? "'" + field + "'" : "A field")
                        + " received '" + value + "', which is not a supported value. "
                        + "Accepted values: " + accepted + ".";
            }

            if (field != null) {
                return "'" + field + "' received '" + value
                        + "', which is not a valid value for that field.";
            }
        }

        return "The request body could not be read. Check the submitted values and try again.";
    }

    private String lastFieldName(InvalidFormatException ex) {
        List<JsonMappingException.Reference> path = ex.getPath();
        if (path == null || path.isEmpty()) return null;
        return path.get(path.size() - 1).getFieldName();
    }
}
