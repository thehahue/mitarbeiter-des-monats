package at.bbrz.mitarbeiter.controller;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidEntries(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .distinct().sorted().collect(Collectors.joining(" "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail invalidJson() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Bitte ein gültiges JSON im Format des Endpunkts senden: einen Namen zum Anlegen oder eine Mitarbeiterliste mit Namen und ganzzahligen Rankings zum Speichern.");
    }
}
