package dev.learning.stockanalyzer.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "stock.desktop.enabled", havingValue = "true")
public class DesktopApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DesktopApplicationService.class);

    private final ApplicationContext context;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "stock-lens-desktop");
        thread.setDaemon(true);
        return thread;
    });
    private volatile TrayIcon trayIcon;
    private volatile URI applicationUri;

    public DesktopApplicationService(ApplicationContext context) {
        this.context = context;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        if (!(context instanceof ServletWebServerApplicationContext webContext)) return;
        applicationUri = URI.create("http://localhost:" + webContext.getWebServer().getPort() + "/");
        installTrayIcon();
        executor.schedule(this::openBrowserSafely, 700, TimeUnit.MILLISECONDS);
    }

    private void installTrayIcon() {
        if (!SystemTray.isSupported()) return;
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open Stock Lens");
            open.addActionListener(event -> executor.execute(this::openBrowserSafely));
            MenuItem exit = new MenuItem("Exit");
            exit.addActionListener(event -> exitApplication());
            menu.add(open);
            menu.addSeparator();
            menu.add(exit);
            TrayIcon icon = new TrayIcon(createTrayImage(), "Stock Lens", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(event -> executor.execute(this::openBrowserSafely));
            SystemTray.getSystemTray().add(icon);
            trayIcon = icon;
        } catch (Exception e) {
            log.warn("创建 Stock Lens 托盘图标失败", e);
        }
    }

    private Image createTrayImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(18, 107, 91));
            graphics.fillRoundRect(1, 1, 30, 30, 7, 7);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
            graphics.drawString("S", 10, 23);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void openBrowserSafely() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(applicationUri);
                return;
            }
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", applicationUri.toString()).start();
            }
        } catch (Exception e) {
            log.warn("自动打开 Stock Lens 页面失败，请手动访问 {}", applicationUri, e);
        }
    }

    private void exitApplication() {
        executor.execute(() -> {
            SpringApplication.exit(context);
            System.exit(0);
        });
    }

    @PreDestroy
    void shutdown() {
        TrayIcon icon = trayIcon;
        if (icon != null && SystemTray.isSupported()) SystemTray.getSystemTray().remove(icon);
        executor.shutdownNow();
    }
}
