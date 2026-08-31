package dev.learning.stockanalyzer.data;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StockSearchService {
    private final List<StockInfo> catalog;

    public StockSearchService() {
        this.catalog = loadCatalog();
    }

    public List<StockInfo> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String query = keyword.trim().toLowerCase();
        List<StockInfo> results = catalog.stream()
                .filter(stock -> stock.code().equalsIgnoreCase(query)
                        || stock.fullCode().equalsIgnoreCase(query)
                        || stock.name().toLowerCase().contains(query)
                        || (stock.industry() != null && stock.industry().toLowerCase().contains(query)))
                .toList();
        return results.size() > 30 ? results.subList(0, 30) : results;
    }

    public Optional<StockInfo> findByCode(String code) {
        try {
            String normalized = StockCodeUtils.normalizeFullCode(code);
            return catalog.stream().filter(item -> item.fullCode().equals(normalized)).findFirst();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private List<StockInfo> loadCatalog() {
        List<StockInfo> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("stock-list.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().skip(1).map(line -> line.split(",", -1)).forEach(fields -> {
                if (fields.length >= 4 && fields[0].matches("\\d{6}")) {
                    items.add(new StockInfo(fields[0], fields[1], fields[2], fields[3]));
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("无法加载股票目录", e);
        }
        return List.copyOf(items);
    }
}
