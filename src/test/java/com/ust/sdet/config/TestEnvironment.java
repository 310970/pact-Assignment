package com.ust.sdet.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
public final class TestEnvironment {
    private static final Path ENV_FILE = findEnvFile();
    private static final Map<String, String> LOCAL_ENV = loadLocalEnv(ENV_FILE);
    private TestEnvironment() {
    }
    public static String required(String name) {
        String value = optional(name, null);
        if (value == null) {
            throw new IllegalStateException(
                    "Missing " + name + ". Set it in the IntelliJ run configuration or in .env. "
                            + "Working directory: " + Path.of("").toAbsolutePath()
                            + ". Loaded .env: " + loadedEnvFile()
            );
        }
        return value;
    }
    public static String optional(String name, String fallback) {
        String systemValue = System.getProperty(name);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        String environmentValue = System.getenv(name);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }
        String fileValue = LOCAL_ENV.get(name);
        return fileValue == null || fileValue.isBlank() ? fallback : fileValue;
    }
    public static String loadedEnvFile() {
        return ENV_FILE == null ? "none" : ENV_FILE.toString();
    }
    public static String sourceOf(String name) {
        String systemValue = System.getProperty(name);
        if (systemValue != null && !systemValue.isBlank()) {
            return "JVM system property (-D" + name + ")";
        }
        String environmentValue = System.getenv(name);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return "process environment / IntelliJ run configuration";
        }
        if (LOCAL_ENV.containsKey(name) && !LOCAL_ENV.get(name).isBlank()) {
            return ".env file: " + loadedEnvFile();
        }
        return "not configured";
    }
    private static Path findEnvFile() {
        String explicitPath = System.getProperty("envFile");
        if (explicitPath == null || explicitPath.isBlank()) {
            explicitPath = System.getenv("ENV_FILE");
        }
        if (explicitPath != null && !explicitPath.isBlank()) {
            Path configured = Path.of(explicitPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(configured)) {
                throw new IllegalStateException("Configured ENV_FILE does not exist: " + configured);
            }
            return configured;
        }
        Set<Path> startingPoints = new LinkedHashSet<>();
        startingPoints.add(Path.of("").toAbsolutePath().normalize());
        try {
            Path codeLocation = Path.of(
                    TestEnvironment.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).toAbsolutePath().normalize();
            startingPoints.add(codeLocation);
        } catch (URISyntaxException ignored) {
            // The working-directory search still provides a normal fallback.
        }
        for (Path startingPoint : startingPoints) {
            Path found = findUpwards(startingPoint);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
    private static Path findUpwards(Path startingPoint) {
        Path current = Files.isDirectory(startingPoint) ? startingPoint : startingPoint.getParent();
        while (current != null) {
            Path candidate = current.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }
    private static Map<String, String> loadLocalEnv(Path envFile) {
        if (envFile == null) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring("export ".length()).trim();
                }
                String[] entry = trimmed.split("=", 2);
                values.put(entry[0].trim(), unquote(entry[1].trim()));
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read .env file: " + envFile, error);
        }
        return values;
    }
    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}