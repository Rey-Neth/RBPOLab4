package org.example.controller;

import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<?> business(BadRequestException exception) {
        return error(400, "BUSINESS_RULE", exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformedJson() {
        return error(400, "INVALID_JSON", "Проверьте JSON и типы полей");
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<?> notFound() {
        return error(404, "NOT_FOUND", "Запись не найдена");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> forbidden() {
        return error(403, "ACCESS_DENIED", "Недостаточно прав");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() {
        return error(409, "DATA_CONFLICT", "Логин уже занят или данные нарушают ограничения БД");
    }

    private ResponseEntity<Map<String, String>> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "error", message));
    }
}
