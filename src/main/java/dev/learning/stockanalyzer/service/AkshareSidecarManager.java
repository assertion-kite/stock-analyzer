package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.config.FundamentalsProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class AkshareSidecarManager {

    private static final Logger log = LoggerFactory.getLogger(AkshareSidecarManager.class);

    private final FundamentalsProperties properties;
    private volatile Process process;

    public AkshareSidecarManager(FundamentalsProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startIfConfigured() {
        if (!properties.isEnabled() || !properties.isAutoStart() || isHealthy()) return;

        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<String> command = resolveCommand(workingDirectory);
        if (command == null) return;

        try {
            URI baseUri = URI.create(properties.getBaseUrl());
            int port = baseUri.getPort() > 0 ? baseUri.getPort() : 8765;
            Path logDirectory = resolveLogDirectory(workingDirectory);
            Files.createDirectories(logDirectory);
            Path logFile = logDirectory.resolve("akshare-sidecar.log");

            command.add("--port");
            command.add(String.valueOf(port));
            command.add("--request-timeout");
            command.add(String.valueOf(properties.getRequestTimeoutSeconds()));

            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
            processBuilder.environment().put("PYTHONUTF8", "1");
            processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
            process = processBuilder.start();
            log.info("AKShare data service started: pid={} port={}", process.pid(), port);
        } catch (Exception e) {
            log.warn("Failed to start AKShare data service", e);
        }
    }

    private List<String> resolveCommand(Path workingDirectory) {
        Path packagedExecutable = resolvePackagedSidecar(workingDirectory);
        if (packagedExecutable != null) {
            return new ArrayList<>(List.of(packagedExecutable.toString()));
        }

        Path script = workingDirectory.resolve(properties.getScriptPath()).normalize();
        Path python = resolvePython(workingDirectory);
        if (!Files.isRegularFile(script)) {
            log.warn("AKShare script not found: {}", script);
            return null;
        }
        if (python == null) {
            log.warn("AKShare Python environment not found. Run scripts/setup-akshare.ps1 first.");
            return null;
        }
        return new ArrayList<>(List.of(python.toString(), script.toString()));
    }

    private Path resolvePackagedSidecar(Path workingDirectory) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(workingDirectory.resolve("akshare-sidecar.exe"));
        candidates.add(workingDirectory.resolve("akshare-sidecar/akshare-sidecar.exe"));
        candidates.add(workingDirectory.resolve("app/akshare-sidecar.exe"));
        candidates.add(workingDirectory.resolve("app/akshare-sidecar/akshare-sidecar.exe"));
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            Path launcherDirectory = Path.of(appPath).toAbsolutePath().normalize().getParent();
            if (launcherDirectory != null) {
                candidates.add(launcherDirectory.resolve("app/akshare-sidecar.exe"));
                candidates.add(launcherDirectory.resolve("app/akshare-sidecar/akshare-sidecar.exe"));
            }
        }
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }

    private Path resolvePython(Path workingDirectory) {
        if (properties.getPythonExecutable() != null && !properties.getPythonExecutable().isBlank()) {
            Path configured = Path.of(properties.getPythonExecutable());
            if (!configured.isAbsolute()) configured = workingDirectory.resolve(configured);
            if (Files.isRegularFile(configured)) return configured.normalize();
        }
        List<Path> candidates = List.of(
                workingDirectory.resolve(".venv-akshare/Scripts/python.exe"),
                workingDirectory.resolve(".venv-akshare/bin/python")
        );
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }

    private Path resolveLogDirectory(Path workingDirectory) {
        if (Boolean.getBoolean("stock.app.desktop")) {
            return Path.of(System.getProperty("user.home"), ".stock-lens", "logs");
        }
        return workingDirectory.resolve("target");
    }

    private boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/health"))
                    .timeout(Duration.ofMillis(800))
                    .GET()
                    .build();
            HttpResponse<Void> response = HttpClient.newHttpClient().send(
                    request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception ignored) {
            return false;
        }
    }

    @PreDestroy
    void stop() {
        Process current = process;
        if (current != null && current.isAlive()) current.destroy();
    }
}
