package jp.ac.tsukuba.eclab.assetmarketsimulation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration for CORS and SPA routing
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Allow cross-origin requests for development
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);

        registry.addMapping("/ws/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:3000")
                .allowedMethods("GET", "POST")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static files from classpath:/static/
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward all non-API, non-static routes to index.html for SPA routing
        // This handles Vue Router routes like /market, /stocks, etc.
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/market").setViewName("forward:/index.html");
        registry.addViewController("/stocks").setViewName("forward:/index.html");
        registry.addViewController("/stocks/**").setViewName("forward:/index.html");
        registry.addViewController("/traders").setViewName("forward:/index.html");
        registry.addViewController("/traders/**").setViewName("forward:/index.html");
        registry.addViewController("/macro").setViewName("forward:/index.html");
        registry.addViewController("/sectors").setViewName("forward:/index.html");
        registry.addViewController("/compare").setViewName("forward:/index.html");
        registry.addViewController("/control").setViewName("forward:/index.html");
        registry.addViewController("/results").setViewName("forward:/index.html");
    }
}
