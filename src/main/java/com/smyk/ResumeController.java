package com.smyk;

import java.util.Map;
import org.apache.tika.Tika;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/resume")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ResumeController {

    private final ChatClient chatClient;
    private final Tika tika = new Tika();

    public ResumeController(OllamaChatModel ollamaChatModel) {
        this.chatClient = ChatClient.create(ollamaChatModel);
    }

    // Direct Preflight OPTIONS Handler
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

        String aiResponse = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

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

        String aiResponse = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        return Map.of("atsReport", aiResponse);
    }
}