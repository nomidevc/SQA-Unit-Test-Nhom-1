package com.doan.WEB_TMDT.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class DotenvConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        
        Map<String, Object> envVars = loadEnvFile();
        if (!envVars.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("dotenv", envVars));
            System.out.println("✅ Loaded " + envVars.size() + " variables from .env file");
        }
    }

    private Map<String, Object> loadEnvFile() {
        Map<String, Object> envVars = new HashMap<>();
        
        // Try to find .env file in project root
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            envPath = Paths.get(System.getProperty("user.dir"), ".env");
        }
        
        if (!Files.exists(envPath)) {
            System.out.println("⚠️ .env file not found at: " + envPath.toAbsolutePath());
            return envVars;
        }

        try {
            Files.readAllLines(envPath).forEach(line -> {
                // Skip comments and empty lines
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    return;
                }
                
                // Parse KEY=VALUE
                int equalIndex = trimmed.indexOf('=');
                if (equalIndex > 0) {
                    String key = trimmed.substring(0, equalIndex).trim();
                    String value = trimmed.substring(equalIndex + 1).trim();
                    
                    // Remove quotes if present
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    envVars.put(key, value);
                }
            });
        } catch (IOException e) {
            System.err.println("❌ Error reading .env file: " + e.getMessage());
        }
        
        return envVars;
    }
}
