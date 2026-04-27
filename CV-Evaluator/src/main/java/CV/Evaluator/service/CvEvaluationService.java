package CV.Evaluator.service;

import CV.Evaluator.dto.CvEvaluationResponse;
import CV.Evaluator.exception.CvProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CvEvaluationService {

    @Value("${openrouter.api.key}")
    private String apiKey;

    @Value("${openrouter.api.url}")
    private String apiUrl;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CvEvaluationResponse evaluate(MultipartFile file) {
        try {
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());

            String response = webClientBuilder.build()
                    .post()
                    .uri(apiUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "http://localhost:8080")
                    .header("X-Title", "CV-Evaluator")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(buildRequest(base64, file.getContentType()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String content = extractContent(response);
            return objectMapper.readValue(cleanJson(content), CvEvaluationResponse.class);

        } catch (Exception e) {
            throw new RuntimeException("CV processing failed: " + e.getMessage());
        }
    }


    private Map<String, Object> buildRequest(String base64, String mimeType) {
        return Map.of(
                "model", "openrouter/free",
                "messages", new Object[]{
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "text", "text", buildPrompt()),
                                        Map.of("type", "image_url",
                                                "image_url", Map.of(
                                                        "url", "data:" + (mimeType != null ? mimeType : "application/pdf")
                                                                + ";base64," + base64
                                                ))
                                )
                        )
                }
        );
    }

    private String extractContent(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        return root.at("/choices/0/message/content").asText();
    }

    private String cleanJson(String text) {
        return text.replace("```json", "")
                .replace("```", "")
                .trim();
    }

    private String buildPrompt() {
        return """
                Evaluate the CV and return JSON only:

                Scores (0-10):
                - formatting
                - content
                - skills
                - experience
                - professionalism

                Also return:
                - total_score (out of 50)
                - percentage
                - strengths (2)
                - weaknesses (2)
                - suggestions (2)

                JSON format:
                {
                  "formatting_score": number,
                  "content_score": number,
                  "skills_score": number,
                  "experience_score": number,
                  "professionalism_score": number,
                  "total_score": number,
                  "percentage": number,
                  "strengths": [],
                  "weaknesses": [],
                  "suggestions": []
                }
                """;
    }
}