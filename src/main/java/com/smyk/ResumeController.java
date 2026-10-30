package com.smyk;

import java.util.List;
import java.util.Map;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/resume")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ResumeController {

    private final Tika tika = new Tika();
    private final RestClient restClient;

    public ResumeController(@Value("${GROQ_API_KEY:}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.groq.com/openai")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/analyser")
    public ResponseEntity<?> analyser(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is empty"));
            }

            String content = tika.parseToString(file.getInputStream());

            String prompt = """
             You are an expert HR resume evaluator. Analyze this resume text:
             %s

             Provide:
             1. Key Skills
             2. Overall Quality Rating (1-10)
             3. 3 Key Improvements
             """.formatted(content);

            String aiResponse = callGroq(prompt);
            return ResponseEntity.ok(Map.of("analysis", aiResponse));

        } catch (Exception e) {
            e.printStackTrace(); // Prints exact error in Render logs
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Analysis failed: " + e.getMessage()));
        }
    }

    @PostMapping("/ats-check")
    public ResponseEntity<?> analyseATS(@RequestParam("file") MultipartFile file,
                                          @RequestParam("jd") String jobDescription) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is empty"));
            }

            String resumeText = tika.parseToString(file.getInputStream());

            String prompt = """
             You are an expert ATS analyzer. Compare the resume and job description.

             Resume Text:
             %s

             Job Description:
             %s

             Analyze ATS match score, matching keywords, missing keywords from JD, and summary.
             """.formatted(resumeText, jobDescription);

            String aiResponse = callGroq(prompt);
            return ResponseEntity.ok(Map.of("atsReport", aiResponse));

        } catch (Exception e) {
            e.printStackTrace(); // Prints exact error in Render logs
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "ATS Check failed: " + e.getMessage()));
        }
    }

    private String callGroq(String userPrompt) {
        Map<String, Object> requestBody = Map.of(
            "model", "openai/gpt-oss-20b",
            "messages", List.of(
                Map.of("role", "user", "content", userPrompt)
            ),
            "temperature", 0.2
        );

        Map<?, ?> response = restClient.post()
                .uri("/v1/chat/completions")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response != null && response.containsKey("choices")) {
            List<?> choices = (List<?>) response.get("choices");
            if (!choices.isEmpty()) {
                Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
                Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
                return (String) message.get("content");
            }
        }
        return "{}";
    }
}