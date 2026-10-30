package com.smyk;

import java.util.Map;

import org.apache.tika.Tika;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/resume")
@CrossOrigin(origins="*",allowedHeaders = "*")
public class ResumeController {

	private final ChatClient chatClient;
	
	private final Tika tika=new Tika();

	public ResumeController(OllamaChatModel ollamaChatModel) {
		this.chatClient = ChatClient.create(ollamaChatModel);
	}
	
	@PostMapping("/analyser")  
	public Map<String, Object> analyser(@RequestParam("file") MultipartFile file) throws Exception {  
	    String content = tika.parseToString(file.getInputStream());  
	      
	    String prompt = """
	    	    Analyze this resume text:
	    	    %s

	    	    Return your response strictly as a JSON object matching this exact structure:
	    	    {
	    	      "keySkills": ["skill1", "skill2"],
	    	      "overallRating": 7,
	    	      "improvements": ["improvement1", "improvement2", "improvement3"]
	    	    }

	    	    Do NOT change these key names. Output ONLY valid raw JSON.
	    	    """.formatted(content);
	    
	    String aiResponse = chatClient.prompt()  
	        .user(prompt)  
	        .call()  
	        .content();  
	      
	    // Strip markdown code fences if Ollama wraps output in ```json ... ```
	    if (aiResponse != null) {
	        aiResponse = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();
	    }

	    return Map.of("analysis", aiResponse);  
	}
	
	@PostMapping("/ats-check")  
	public Map<String, Object> analyseATS(@RequestParam("file") MultipartFile file,  
	                                      @RequestParam("jd") String jobDescription) throws Exception {  
	    String resumeText = tika.parseToString(file.getInputStream());  

	    String prompt = """
	        You are an expert ATS (Applicant Tracking System) analyzer. 
	        Perform a strict, unidirectional keyword match between the provided Resume and Job Description.

	        --- RESUME ---
	        %s

	        --- JOB DESCRIPTION ---
	        %s

	        INSTRUCTIONS:
	        1. "atsScore": Evaluate overall match quality (0-100).
	        2. "matchedKeywords": List keywords/skills present in BOTH the Resume and the Job Description.
	        3. "missingKeywords": List ONLY keywords, tools, or skills required in the Job Description that are MISSING from the Resume. 
	           CRITICAL: Do NOT list skills present in the Resume if they are absent from the Job Description. (Formally: Job Description keywords minus Resume keywords).
	        4. "summary": A brief 2-3 sentence overview of the candidate's alignment with the role.

	        Return strictly valid raw JSON in this format:
	        {
	          "atsScore": 85,
	          "matchedKeywords": ["Java", "Spring Boot"],
	          "missingKeywords": ["Docker", "Kubernetes"],
	          "summary": "Candidate matches primary backend requirements but lacks cloud containerization skills."
	        }
	        """.formatted(resumeText, jobDescription);  

	    String aiResponse = chatClient.prompt()  
	        .user(prompt)  
	        .call()  
	        .content();  

	    return Map.of("atsReport", aiResponse);  
	}
	// Add inside ResumeController class
    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok().build();
    }
	
}
