package com.smyk;

import java.util.List;
import java.util.Map;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
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
                .baseUrl("https://api.groq.com/openai/v1/chat/completions")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/analyser")
    public Map<String, Object> analyser(@RequestParam("file") MultipartFile file) throws Exception {
        String content = tika.parseToString(file.getInputStream());

        String prompt = """
         Analyse this resume text:
         %s
         1. Extract Key skills
         2. Rate overall resume quality (1-10)
         3. Suggest 3 improvements
         Reply in structured JSON format.
         """.formatted(content);

        String aiResponse = callGroq(prompt);
        return Map.of("analysis", aiResponse);
    }

    @PostMapping("/ats-check")
    public Map<String, Object> analyseATS(@RequestParam("file") MultipartFile file,
                                          @RequestParam("jd") String jobDescription) throws Exception {
        String resumeText = tika.parseToString(file.getInputStream());

        String prompt = """
         You are an expert ATS analyzer. Compare the resume and job description.

         -----
         Resume:
         %s
         -----
         Job Description:
         %s
         ----

         Analyse and return a structured JSON with:
         1. "atsScore" (0-100)
         2. "matchedKeywords" (list)
         3. "missingKeywords" (list)
         4. "summary" (short paragraph)

         keep response strictly valid json.
         """.formatted(resumeText, jobDescription);

        String aiResponse = callGroq(prompt);
        return Map.of("atsReport", aiResponse);
    }

    private String callGroq(String userPrompt) {
        Map<String, Object> requestBody = Map.of(
            "model", "llama-3.3-70b-versatile",
            "messages", List.of(
                Map.of("role", "user", "content", userPrompt)
            ),
            "temperature", 0.2
        );

        Map<?, ?> response = restClient.post()
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