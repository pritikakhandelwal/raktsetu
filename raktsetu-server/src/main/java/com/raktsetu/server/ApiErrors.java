package com.raktsetu.server;

import com.raktsetu.domain.exception.FacilityNotFoundException;
import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.server.ApiModels.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns expected problems into JSON errors with a message staff can read: unknown facility is 404, bad input is 400,
 * not allowed for this account is 403, and a refused operation (not enough stock, unsafe unit, changed stock) is 409.
 */
@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(FacilityNotFoundException.class)
    public ResponseEntity<ApiError> notFound(FacilityNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(RaktSetuException.class)
    public ResponseEntity<ApiError> refused(RaktSetuException e) {
        return error(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("Forbidden", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badInput(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ApiError("BadRequest",
                "The request body is not valid JSON of the expected shape"));
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, Exception e) {
        return ResponseEntity.status(status).body(new ApiError(e.getClass().getSimpleName(), e.getMessage()));
    }
}
