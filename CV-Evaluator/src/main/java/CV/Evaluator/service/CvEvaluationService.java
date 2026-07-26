package CV.Evaluator.service;

import CV.Evaluator.dto.CvEvaluationResponse;
import CV.Evaluator.exception.CvProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    // Supported MIME types grouped by processing strategy
    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );
    private static final Set<String> TEXT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/msword"  // .doc
    );

    // ──────────────────────────────────────────────────────────────
    //  PUBLIC ENTRY POINT
    // ──────────────────────────────────────────────────────────────

    public CvEvaluationResponse evaluate(MultipartFile file) {
        String mimeType = resolveMimeType(file);
        log.info("Evaluating CV — filename: {}, detected mime: {}", file.getOriginalFilename(), mimeType);

        try {
            String rawResponse;

            if (IMAGE_TYPES.contains(mimeType)) {
                // JPG / PNG → send directly as base64 image_url (vision)
                rawResponse = callApi(buildImageRequest(file, mimeType));

            } else if (mimeType.equals("application/pdf")) {
                // PDF — try text extraction first
                String extractedText = extractFromPdf(file);

                if (!extractedText.isBlank()) {
                    // Text-based PDF → send as text prompt
                    log.info("Text-based PDF: extracted {} characters.", extractedText.length());
                    rawResponse = callApi(buildTextRequest(extractedText));
                } else {
                    // Image-based (scanned) PDF → render pages → send as vision request
                    log.info("Image-based PDF detected — rendering pages for vision API.");
                    List<String> pageBase64List = renderPdfPagesToBase64(file);
                    rawResponse = callApi(buildMultiImageRequest(pageBase64List));
                }

            } else if (mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    || mimeType.equals("application/msword")) {
                // DOCX / DOC → extract text → send as text prompt
                String extractedText = extractText(file, mimeType);
                if (extractedText.isBlank()) {
                    throw new CvProcessingException(
                            "Could not extract readable text from the uploaded Word document."
                    );
                }
                log.info("Extracted {} characters from Word document.", extractedText.length());
                rawResponse = callApi(buildTextRequest(extractedText));

            } else {
                throw new CvProcessingException(
                        "Unsupported file type: " + mimeType +
                                ". Supported types: PDF, DOCX, DOC, JPG, PNG."
                );
            }

            String content = extractContent(rawResponse);
            return objectMapper.readValue(cleanJson(content), CvEvaluationResponse.class);

        } catch (CvProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("CV evaluation error", e);
            throw new CvProcessingException("CV processing failed: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  MIME TYPE RESOLUTION
    //  (File extension fallback — browsers sometimes lie about MIME)
    // ──────────────────────────────────────────────────────────────

    private String resolveMimeType(MultipartFile file) {
        String mime = file.getContentType();
        String name = file.getOriginalFilename();

        // If mime is missing/generic, infer from extension
        if (mime == null || mime.equals("application/octet-stream")) {
            if (name != null) {
                String lower = name.toLowerCase();
                if (lower.endsWith(".pdf"))  return "application/pdf";
                if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                if (lower.endsWith(".doc"))  return "application/msword";
                if (lower.endsWith(".png"))  return "image/png";
                if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            }
        }
        return mime != null ? mime : "application/octet-stream";
    }

    // ──────────────────────────────────────────────────────────────
    //  TEXT EXTRACTION
    // ──────────────────────────────────────────────────────────────

    private String extractText(MultipartFile file, String mimeType) throws Exception {
        return switch (mimeType) {
            case "application/pdf" -> extractFromPdf(file);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> extractFromDocx(file);
            case "application/msword" -> extractFromDoc(file);
            default -> throw new CvProcessingException("No text extractor for mime type: " + mimeType);
        };
    }

    /** PDF → text via Apache PDFBox v3. Returns empty string if PDF is image-based. */
    private String extractFromPdf(MultipartFile file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.isEncrypted()) {
                throw new CvProcessingException("The PDF is password-protected and cannot be read.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document).trim();
        }
    }

    /**
     * Renders each page of an image-based PDF into a PNG and encodes it as base64.
     * Renders up to 3 pages (enough for a CV) to stay within API limits.
     */
    private List<String> renderPdfPagesToBase64(MultipartFile file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFRenderer renderer = new PDFRenderer(document);
            List<String> pages = new ArrayList<>();

            int pageCount = Math.min(document.getNumberOfPages(), 3); // max 3 pages
            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", baos);
                pages.add(Base64.getEncoder().encodeToString(baos.toByteArray()));
                log.info("Rendered PDF page {} as image ({} bytes).", i + 1, baos.size());
            }
            return pages;
        }
    }

    /** DOCX → text via Apache POI XWPF */
    private String extractFromDocx(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {

            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(t -> !t.isBlank())
                    .collect(Collectors.joining("\n"))
                    .trim();
        }
    }

    /** DOC → text via Apache POI HWPF */
    private String extractFromDoc(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             HWPFDocument document = new HWPFDocument(is);
             WordExtractor extractor = new WordExtractor(document)) {

            return extractor.getText().trim();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  REQUEST BUILDERS
    // ──────────────────────────────────────────────────────────────

    /** For JPG / PNG — send file as base64 image_url (vision) */
    private Map<String, Object> buildImageRequest(MultipartFile file, String mimeType) throws Exception {
        String base64 = Base64.getEncoder().encodeToString(file.getBytes());

        return Map.of(
                "model", "openrouter/auto",
                "messages", new Object[]{
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "text", "text", buildPrompt()),
                                        Map.of("type", "image_url",
                                                "image_url", Map.of(
                                                        "url", "data:" + mimeType + ";base64," + base64
                                                ))
                                )
                        )
                }
        );
    }

    /**
     * For image-based PDFs — sends each rendered page as a separate image_url entry.
     * The vision model sees all pages and evaluates the full CV.
     */
    private Map<String, Object> buildMultiImageRequest(List<String> pageBase64List) {
        List<Map<String, Object>> contentParts = new ArrayList<>();

        // First part: the evaluation prompt
        contentParts.add(Map.of("type", "text", "text", buildPrompt()));

        // Each rendered page as an image_url
        for (int i = 0; i < pageBase64List.size(); i++) {
            contentParts.add(Map.of(
                    "type", "text",
                    "text", "Page " + (i + 1) + ":"
            ));
            contentParts.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of(
                            "url", "data:image/png;base64," + pageBase64List.get(i)
                    )
            ));
        }

        return Map.of(
                "model", "openrouter/auto",
                "messages", new Object[]{
                        Map.of("role", "user", "content", contentParts)
                }
        );
    }

    /** For PDF / DOCX / DOC — send extracted text as a plain text message */
    private Map<String, Object> buildTextRequest(String extractedText) {
        String fullPrompt = buildPrompt() +
                "\n\n--- CV CONTENT START ---\n" +
                extractedText +
                "\n--- CV CONTENT END ---";

        return Map.of(
                "model", "openrouter/auto",
                "messages", new Object[]{
                        Map.of(
                                "role", "user",
                                "content", fullPrompt
                        )
                }
        );
    }

    // ──────────────────────────────────────────────────────────────
    //  HTTP CALL
    // ──────────────────────────────────────────────────────────────

    private String callApi(Map<String, Object> requestBody) {
        return webClientBuilder.build()
                .post()
                .uri(apiUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", "http://localhost:8080")
                .header("X-Title", "CV-Evaluator")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    // ──────────────────────────────────────────────────────────────
    //  RESPONSE PARSING
    // ──────────────────────────────────────────────────────────────

    private String extractContent(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);

        // Log any API-level errors clearly
        JsonNode error = root.get("error");
        if (error != null) {
            throw new CvProcessingException("API error: " + error.path("message").asText(error.toString()));
        }

        String content = root.at("/choices/0/message/content").asText();
        if (content.isBlank()) {
            throw new CvProcessingException("Empty response from AI model. Raw: " + response);
        }
        return content;
    }

    private String cleanJson(String text) {
        // Strip markdown code fences if model wraps JSON in them
        return text.replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
    }

    // ──────────────────────────────────────────────────────────────
    //  PROMPT
    // ──────────────────────────────────────────────────────────────

    private String buildPrompt() {
        return """
                You are a professional CV/Resume evaluator. Evaluate the CV provided and return ONLY valid JSON — no explanation, no markdown, no extra text.

                Score each category from 0 to 10:
                - formatting_score   : Layout, spacing, readability, visual structure
                - content_score      : Clarity, relevance, and quality of written content
                - skills_score       : Relevance and depth of listed technical/soft skills
                - experience_score   : Quality and impact of work experience / projects
                - professionalism_score : Overall professional tone, grammar, and polish

                Also include:
                - total_score  : Sum of all 5 scores (out of 50)
                - percentage   : (total_score / 50) * 100, rounded to nearest integer
                - strengths    : Array of exactly 2 strings — specific things done well
                - weaknesses   : Array of exactly 2 strings — specific areas that need improvement
                - suggestions  : Array of exactly 2 strings — actionable improvement steps

                Return ONLY this JSON structure:
                {
                  "formatting_score": number,
                  "content_score": number,
                  "skills_score": number,
                  "experience_score": number,
                  "professionalism_score": number,
                  "total_score": number,
                  "percentage": number,
                  "strengths": ["...", "..."],
                  "weaknesses": ["...", "..."],
                  "suggestions": ["...", "..."]
                }
                """;
    }
}