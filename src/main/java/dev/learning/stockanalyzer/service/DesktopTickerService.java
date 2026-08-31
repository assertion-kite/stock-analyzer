package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.data.StockDataService;
import dev.learning.stockanalyzer.data.StockQuote;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class DesktopTickerService {
    private final StockDataService dataService;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stock-lens-ticker");
        t.setDaemon(true);
        return t;
    });
    private volatile ScheduledFuture<?> task;
    private volatile List<String> codes = List.of();
    private volatile int intervalSeconds = 5;

    public DesktopTickerService(StockDataService dataService) {
        this.dataService = dataService;
    }

    public synchronized MonitorStatus start(List<String> requestedCodes, Integer requestedInterval) {
        if (requestedCodes == null || requestedCodes.isEmpty()) throw new IllegalArgumentException("至少选择一只股票");
        List<String> normalized = requestedCodes.stream().map(dev.learning.stockanalyzer.data.StockCodeUtils::normalizeFullCode).distinct().toList();
        codes = normalized;
        intervalSeconds = normalizeIntervalSeconds(requestedInterval);
        if (task != null) task.cancel(false);
        task = executor.scheduleAtFixedRate(this::refresh, 0, intervalSeconds, TimeUnit.SECONDS);
        return status();
    }

    public synchronized MonitorStatus stop() {
        if (task != null) task.cancel(false);
        task = null;
        codes = List.of();
        return status();
    }

    public MonitorStatus status() {
        boolean active = task != null && !task.isCancelled() && !task.isDone();
        return new MonitorStatus(active, codes, intervalSeconds, codes.size());
    }

    public static int normalizeIntervalSeconds(Integer value) {
        int seconds = value == null ? 5 : value;
        if (seconds < 1 || seconds > 300) throw new IllegalArgumentException("盯盘刷新间隔必须在1到300秒之间");
        return seconds;
    }

    private void refresh() {
        try {
            List<StockQuote> ignored = dataService.getQuotes(codes);
        } catch (RuntimeException ignored) {
        }
    }

    @PreDestroy
    void shutdown() {
        if (task != null) task.cancel(false);
        executor.shutdownNow();
    }

    public record MonitorStatus(boolean active, List<String> codes, int intervalSeconds, int count) {
        public MonitorStatus { codes = codes == null ? List.of() : List.copyOf(codes); }
    }
}
