# 📄 CV Evaluator

A Spring Boot REST API that evaluates CVs using AI. Upload a CV as a PDF or image and receive a detailed structured score covering formatting, content, skills, experience, and professionalism — powered by **OpenRouter's free AI models** (no credit card required).

---

## 🚀 Features

- 📎 Upload CV as **PDF or image** (JPG, PNG)
- 🤖 AI-powered evaluation via **OpenRouter free tier**
- 📊 Scores across 5 dimensions (out of 10 each)
- ✅ Returns strengths, weaknesses & suggestions
- ⚡ Built with **Spring Boot + WebFlux WebClient**

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.x |
| HTTP Client | Spring WebFlux WebClient |
| AI Provider | OpenRouter (free tier) |
| Build Tool | Maven |
| Utilities | Lombok, Jackson |

---

## 📁 Project Structure

```
src/main/java/CV/Evaluator/
├── config/
│   └── WebClientConfig.java        # WebClient bean
├── controller/
│   └── CvEvaluationController.java # REST endpoint
├── dto/
│   ├── CvEvaluationRequest.java    # Request DTO
│   └── CvEvaluationResponse.java   # Response DTO
├── exception/
│   ├── CvProcessingException.java  # Custom exception
│   └── GlobalExceptionHandler.java # Global error handler
└── service/
    └── CvEvaluationService.java    # Core AI logic
```

---

## ⚙️ Setup & Installation

### 1. Prerequisites
- Java 21+
- Maven 3.8+

### 2. Get a Free API Key
1. Go to [openrouter.ai/keys](https://openrouter.ai/keys)
2. Sign up for free (no credit card needed)
3. Click **Create Key** — leave the allowed hosts field **empty**
4. Copy your key

### 3. Set Environment Variable

**Windows (Command Prompt):**
```cmd
set OPENROUTER_API_KEY=sk-or-v1-your-key-here
```

**Windows (PowerShell):**
```powershell
$env:OPENROUTER_API_KEY="sk-or-v1-your-key-here"
```

**Linux / macOS:**
```bash
export OPENROUTER_API_KEY=sk-or-v1-your-key-here
```

**IntelliJ IDEA:**
> Run → Edit Configurations → Environment Variables → add `OPENROUTER_API_KEY=sk-or-v1-your-key-here`

### 4. Clone & Run

```bash
git clone https://github.com/your-username/cv-evaluator.git
cd cv-evaluator
mvn spring-boot:run
```

The server starts on **http://localhost:8080**

---

## 📬 API Usage

### Endpoint

```
POST /api/v1/cv/evaluate
```

### Request

| Field | Type | Description |
|---|---|---|
| `file` | `MultipartFile` | CV file (PDF, PNG, JPG) — max 10MB |

**Postman setup:**
- Method: `POST`
- URL: `http://localhost:8080/api/v1/cv/evaluate`
- Body: `form-data`
- Key: `file` → Type: **File** ← (not Text!)
- Value: select your CV file

### Response

```json
{
  "formattingScore": 8,
  "contentScore": 7,
  "skillsScore": 9,
  "experienceScore": 6,
  "professionalismScore": 8,
  "totalScore": 38,
  "percentage": 76,
  "strengths": [
    "Clear section structure with consistent formatting",
    "Strong technical skills section with relevant tools"
  ],
  "weaknesses": [
    "Work experience lacks quantifiable achievements",
    "No summary or objective statement at the top"
  ],
  "suggestions": [
    "Add measurable impact to each job description (e.g. 'reduced load time by 30%')",
    "Include a 2-3 line professional summary at the top of the CV"
  ]
}
```

### Error Response

```json
"CV processing failed: <reason>"
```

---

## 🔐 Environment Variables

| Variable | Description |
|---|---|
| `OPENROUTER_API_KEY` | Your OpenRouter API key (required) |

---

## 📝 Configuration (`application.properties`)

```properties
openrouter.api.key=${OPENROUTER_API_KEY}
openrouter.api.url=https://openrouter.ai/api/v1

spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

---

## ⚠️ Important Notes

- **Never commit your API key** to GitHub. It is read from an environment variable for this reason.
- OpenRouter's free tier auto-selects from 29+ free models — no configuration needed.
- Supported file types: `application/pdf`, `image/png`, `image/jpeg`

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).
