package CV.Evaluator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)   // ← ADD THIS LINE
public class CvEvaluationResponse {

    @JsonProperty("formatting_score")
    private int formattingScore;

    @JsonProperty("content_score")
    private int contentScore;

    @JsonProperty("skills_score")
    private int skillsScore;

    @JsonProperty("experience_score")
    private int experienceScore;

    @JsonProperty("professionalism_score")
    private int professionalismScore;

    @JsonProperty("total_score")
    private int totalScore;

    private int percentage;

    private List<String> strengths;

    private List<String> weaknesses;

    private List<String> suggestions;
}