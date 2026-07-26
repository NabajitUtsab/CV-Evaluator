package CV.Evaluator.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CvProcessingException.class)
    ResponseEntity<String> handleCvProcessingException(CvProcessingException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
