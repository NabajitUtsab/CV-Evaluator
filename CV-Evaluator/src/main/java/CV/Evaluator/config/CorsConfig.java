package CV.Evaluator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "https://YOUR_USERNAME.github.io",   // ← GitHub Pages URL
                        "http://localhost:5500",              // ← Live Server (VS Code)
                        "http://127.0.0.1:5500",             // ← Live Server alternate
                        "http://localhost:8080",              // ← Spring Boot itself
                        "http://localhost:63343",             // ← IntelliJ built-in server
                        "http://127.0.0.1:63343",          // ← IntelliJ alternate
                        "https://nabajitutsab.github.io"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}