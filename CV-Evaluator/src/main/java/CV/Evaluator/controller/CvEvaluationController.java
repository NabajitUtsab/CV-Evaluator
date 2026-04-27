package CV.Evaluator.controller;

import CV.Evaluator.dto.CvEvaluationResponse;
import CV.Evaluator.service.CvEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/cv")
@RequiredArgsConstructor
public class CvEvaluationController {

    private final CvEvaluationService cvEvaluationService;

    @PostMapping(value = "/evaluate", consumes = "multipart/form-data")
    public ResponseEntity<CvEvaluationResponse> evaluate(
            @RequestParam("file") MultipartFile file) {

        return ResponseEntity.ok(cvEvaluationService.evaluate(file));
    }
}