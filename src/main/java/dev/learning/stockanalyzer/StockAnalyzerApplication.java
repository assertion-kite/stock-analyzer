package dev.learning.stockanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class StockAnalyzerApplication {

    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public static void main(String[] args) {
        prepareDesktopDirectories();
        SpringApplication application = new SpringApplication(StockAnalyzerApplication.class);
        application.setHeadless(false);
        application.run(args);
    }

    private static void prepareDesktopDirectories() {
        if (!Boolean.getBoolean("stock.app.desktop")) return;
        try {
            Path appData = Path.of(System.getProperty("user.home"), ".stock-lens");
            Files.createDirectories(appData.resolve("data"));
            Path logs = appData.resolve("logs");
            Files.createDirectories(logs);
            ensureUtf8Bom(logs.resolve("stock-lens.log"));
            ensureUtf8Bom(logs.resolve("akshare-sidecar.log"));
        } catch (Exception e) {
            throw new IllegalStateException("无法创建 Stock Lens 用户数据目录", e);
        }
    }

    private static void ensureUtf8Bom(Path logFile) throws Exception {
        if (Files.notExists(logFile)) {
            Files.write(logFile, UTF_8_BOM, StandardOpenOption.CREATE_NEW);
            return;
        }
        try (var input = Files.newInputStream(logFile)) {
            byte[] prefix = input.readNBytes(UTF_8_BOM.length);
            if (java.util.Arrays.equals(prefix, UTF_8_BOM)) return;
        }

        Path temporary = Files.createTempFile(logFile.getParent(), "stock-lens-log-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
                output.write(UTF_8_BOM);
                Files.copy(logFile, output);
            }
            Files.move(temporary, logFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
