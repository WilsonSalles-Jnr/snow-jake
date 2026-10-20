package com.boreal.snowjake;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser.LASTINPUTINFO;

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.util.Arrays;
import java.util.Random;

public class SnowJakeApp extends Application {

    private static final double BAR_HEIGHT = 50.0;
    private static final double SIMULATION_INTERVAL = 0.08;
    private static final double SPAWN_INTERVAL = 0.15;
    private static final int PREVIEW_SECONDS = 10;

    // Janelas
    private Stage settingsStage;
    private Stage overlayStage;

    // Canvas e simulação
    private Canvas canvas;
    private GraphicsContext graphics;
    private boolean[][] grid;
    private int[][] windDirection;

    private int columns;
    private int rows;
    private int flakeSize = 5;

    private final Random random = new Random();

    // Preferências
    private int idleSeconds = 300;
    private String colorMode = "Azul-gelo";
    private String areaMode = "Barra de tarefas";

    // Controles das configurações
    private Spinner<Integer> hoursSpinner;
    private Spinner<Integer> minutesSpinner;
    private Spinner<Integer> secondsSpinner;
    private Spinner<Integer> flakeSizeSpinner;

    private ComboBox<String> colorSelector;
    private ComboBox<String> areaSelector;

    // Novas configurações
    private String movementMode = "Aleatório";
    private String volumeMode = "Aleatório";
    private String speedMode = "Normal";

    // Controles da interface
    private ComboBox<String> movementSelector;
    private ComboBox<String> volumeSelector;
    private ComboBox<String> speedSelector;

    // Controle da geração de flocos
    private double nextSpawnInterval = 0.15;
    private int movementStep;

    // Execução
    private boolean configured;
    private boolean snowing;
    private boolean previewing;

    private double simulationTimer;
    private double spawnTimer;
    private long lastFrame;

    private Timeline idleMonitor;
    private Timeline previewTimer;
    private AnimationTimer animationTimer;

    // Ícone da bandeja do Windows
    private SystemTray systemTray;
    private TrayIcon trayIcon;

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);

        settingsStage = primaryStage;
        settingsStage.setTitle("SnowJake — Configurações");
        settingsStage.initStyle(StageStyle.UTILITY);

        createSettingsWindow();
        configureOverlay();
        initializeAnimation();
        initializeIdleMonitor();
        initializeTray();

        settingsStage.show();
    }

    // --------------------------------------------------
    // CONFIGURAÇÕES
    // --------------------------------------------------

    private void createSettingsWindow() {
        hoursSpinner = new Spinner<>(0, 23, 0);
        minutesSpinner = new Spinner<>(0, 59, 5);
        secondsSpinner = new Spinner<>(0, 59, 0);

        flakeSizeSpinner = new Spinner<>(2, 10, 5);

        hoursSpinner.setEditable(false);
        minutesSpinner.setEditable(false);
        secondsSpinner.setEditable(false);
        flakeSizeSpinner.setEditable(false);

        hoursSpinner.setPrefWidth(85);
        minutesSpinner.setPrefWidth(85);
        secondsSpinner.setPrefWidth(85);
        flakeSizeSpinner.setPrefWidth(100);
        movementSelector = new ComboBox<>();
        movementSelector.getItems().addAll(
                "Reto",
                "Zigue-zague",
                "Aleatório"
        );
        movementSelector.setValue(movementMode);
        movementSelector.setMaxWidth(Double.MAX_VALUE);

        volumeSelector = new ComboBox<>();
        volumeSelector.getItems().addAll(
                "Poucos",
                "Muitos",
                "Aleatório"
        );
        volumeSelector.setValue(volumeMode);
        volumeSelector.setMaxWidth(Double.MAX_VALUE);

        speedSelector = new ComboBox<>();
        speedSelector.getItems().addAll(
                "Lenta",
                "Normal",
                "Rápida"
        );
        speedSelector.setValue(speedMode);
        speedSelector.setMaxWidth(Double.MAX_VALUE);

        colorSelector = new ComboBox<>();
        colorSelector.getItems().addAll(
                "Branco",
                "Azul-gelo",
                "Multicolorido",
                "Verde",
                "Roxo"
        );
        colorSelector.setValue(colorMode);
        colorSelector.setMaxWidth(Double.MAX_VALUE);

        areaSelector = new ComboBox<>();
        areaSelector.getItems().addAll(
                "Barra de tarefas",
                "Tela inteira"
        );
        areaSelector.setValue(areaMode);
        areaSelector.setMaxWidth(Double.MAX_VALUE);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(12);
        gridPane.setVgap(14);
        gridPane.setPadding(new Insets(18));
        gridPane.add(new Label("Movimento da neve"), 0, 4);
        gridPane.add(movementSelector, 1, 4);

        gridPane.add(new Label("Volume da neve"), 0, 5);
        gridPane.add(volumeSelector, 1, 5);

        gridPane.add(new Label("Velocidade de queda"), 0, 6);
        gridPane.add(speedSelector, 1, 6);

        HBox timeFields = new HBox(
                8,
                createTimeField("Horas", hoursSpinner),
                createTimeField("Minutos", minutesSpinner),
                createTimeField("Segundos", secondsSpinner)
        );

        gridPane.add(new Label("Tempo de inatividade"), 0, 0);
        gridPane.add(timeFields, 1, 0);

        gridPane.add(new Label("Paleta de cores"), 0, 1);
        gridPane.add(colorSelector, 1, 1);

        gridPane.add(new Label("Área de neve"), 0, 2);
        gridPane.add(areaSelector, 1, 2);

        gridPane.add(new Label("Tamanho dos flocos"), 0, 3);
        gridPane.add(flakeSizeSpinner, 1, 3);

        Button previewButton = new Button("Pré-visualizar");
        previewButton.setMaxWidth(Double.MAX_VALUE);
        previewButton.setOnAction(event -> previewSnow());

        Button confirmButton = new Button("Confirmar configurações");
        confirmButton.setMaxWidth(Double.MAX_VALUE);
        confirmButton.setDefaultButton(true);
        confirmButton.setOnAction(event -> confirmSettings());

        HBox buttons = new HBox(10, previewButton, confirmButton);
        buttons.setPadding(new Insets(0, 18, 18, 18));
        previewButton.setMaxWidth(Double.MAX_VALUE);
        confirmButton.setMaxWidth(Double.MAX_VALUE);
        previewButton.setPrefWidth(150);
        confirmButton.setPrefWidth(200);

        Label description = new Label(
                "A neve começa quando o tempo de inatividade é atingido."
        );
        description.setWrapText(true);
        description.setPadding(new Insets(0, 18, 12, 18));

        VBox content = new VBox(
                8,
                new Label("Personalize o SnowJake"),
                gridPane,
                description,
                buttons
        );
        content.setPadding(new Insets(12));
        content.setPrefWidth(560);

        settingsStage.setScene(new Scene(content));

        settingsStage.setOnCloseRequest(event -> {
            event.consume();

            if (configured) {
                settingsStage.hide();
            } else {
                shutdown();
            }
        });
    }

    private VBox createTimeField(
            String label,
            Spinner<Integer> spinner
    ) {
        return new VBox(4, new Label(label), spinner);
    }

    private void applySettings() {
        idleSeconds =
                hoursSpinner.getValue() * 3600
                        + minutesSpinner.getValue() * 60
                        + secondsSpinner.getValue();

        flakeSize = flakeSizeSpinner.getValue();
        colorMode = colorSelector.getValue();
        areaMode = areaSelector.getValue();
        movementMode = movementSelector.getValue();
        volumeMode = volumeSelector.getValue();
        speedMode = speedSelector.getValue();

        configureOverlay();
    }

    private double getSimulationInterval() {
        return switch (speedMode) {
            case "Lenta" -> 0.16;
            case "Rápida" -> 0.035;
            default -> 0.08;
        };
    }

    private double getNextSpawnInterval() {
        return switch (volumeMode) {
            case "Poucos" -> 0.35;
            case "Muitos" -> 0.06;
            default -> 0.06 + random.nextDouble() * 0.29;
        };
    }

    private void confirmSettings() {
        try {
            applySettings();

            if (idleSeconds <= 0) {
                showError(
                        "Tempo inválido",
                        "Informe um tempo de inatividade maior que zero."
                );
                return;
            }

            configured = true;
            settingsStage.hide();
            clearSnow();

            if (idleMonitor != null) {
                idleMonitor.playFromStart();
            }

            System.out.println("SnowJake configurado com sucesso.");

        } catch (Exception e) {
            e.printStackTrace();

            showError(
                    "Erro ao confirmar configurações",
                    e.getClass().getSimpleName() + ": " + e.getMessage()
            );
        }
    }

    // --------------------------------------------------
    // JANELA TRANSPARENTE
    // --------------------------------------------------

    private void configureOverlay() {
        Rectangle2D bounds = Screen.getPrimary().getBounds();

        double overlayHeight = areaMode.equals("Tela inteira")
                ? bounds.getHeight()
                : BAR_HEIGHT;

        columns = Math.max(
                1,
                (int) Math.ceil(bounds.getWidth() / flakeSize)
        );

        rows = Math.max(
                1,
                (int) Math.ceil(overlayHeight / flakeSize)
        );

        grid = new boolean[rows][columns];
        windDirection = new int[rows][columns];

        canvas = new Canvas(bounds.getWidth(), overlayHeight);
        canvas.setMouseTransparent(true);

        graphics = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        root.setStyle("-fx-background-color: transparent;");
        root.setMouseTransparent(true);

        Scene scene = new Scene(
                root,
                bounds.getWidth(),
                overlayHeight,
                Color.TRANSPARENT
        );
        scene.setFill(Color.TRANSPARENT);

        if (overlayStage == null) {
            overlayStage = new Stage();
            overlayStage.initStyle(StageStyle.TRANSPARENT);
            overlayStage.setAlwaysOnTop(true);
            overlayStage.setResizable(false);
            overlayStage.setTitle("SnowJake Overlay");
        }

        overlayStage.setScene(scene);
        overlayStage.setWidth(bounds.getWidth());
        overlayStage.setHeight(overlayHeight);
        overlayStage.setX(bounds.getMinX());

        if (areaMode.equals("Tela inteira")) {
            overlayStage.setY(bounds.getMinY());
        } else {
            overlayStage.setY(bounds.getMaxY() - overlayHeight);
        }

        overlayStage.hide();
    }

    // --------------------------------------------------
    // ANIMAÇÃO
    // --------------------------------------------------

    private void initializeAnimation() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!snowing) {
                    lastFrame = 0;
                    return;
                }

                if (lastFrame == 0) {
                    lastFrame = now;
                    return;
                }

                double delta = (now - lastFrame) / 1_000_000_000.0;
                lastFrame = now;

                simulationTimer += delta;
                spawnTimer += delta;

                double interval = getSimulationInterval();

                if (simulationTimer >= interval) {
                    simulationTimer %= interval;

                    if (spawnTimer >= nextSpawnInterval) {
                        spawnTimer = 0;

                        spawnPixel();

                        nextSpawnInterval = getNextSpawnInterval();
                    }

                    updateGrid();
                    render();
                }
            }
        };
        nextSpawnInterval = getNextSpawnInterval();
        animationTimer.start();
    }

    private void spawnPixel() {
        if (grid == null || rows == 0 || columns == 0) {
            return;
        }

        int column = random.nextInt(columns);

        if (!grid[0][column]) {
            grid[0][column] = true;
            windDirection[0][column] = random.nextBoolean() ? 1 : -1;
        }
    }

    private void updateGrid() {
        if (grid == null) {
            return;
        }

        for (int y = rows - 2; y >= 0; y--) {
            for (int x = 0; x < columns; x++) {
                if (!grid[y][x]) {
                    continue;
                }

                int direction = 0;

                switch (movementMode) {
                    case "Reto" -> direction = 0;

                    case "Zigue-zague" -> {
                        direction = (movementStep++ % 2 == 0) ? 1 : -1;
                    }

                    case "Aleatório" -> {
                        if (random.nextBoolean()) {
                            direction = random.nextBoolean() ? 1 : -1;
                        }
                    }

                    default -> direction = 0;
                }

                int nextX = Math.max(
                        0,
                        Math.min(columns - 1, x + direction)
                );

                int nextY = y + 1;

                if (!grid[nextY][nextX]) {
                    grid[y][x] = false;
                    grid[nextY][nextX] = true;
                }
            }
        }

        removeCompletedRows();
    }

    private void removeCompletedRows() {
        for (int y = rows - 1; y >= 0; y--) {
            boolean fullRow = true;

            for (int x = 0; x < columns; x++) {
                if (!grid[y][x]) {
                    fullRow = false;
                    break;
                }
            }

            if (fullRow) {
                // Faz as linhas superiores descerem.
                for (int row = y; row > 0; row--) {
                    grid[row] = grid[row - 1].clone();
                }

                Arrays.fill(grid[0], false);

                // Verifica novamente a linha que recebeu os pixels.
                y++;
            }
        }
    }

    private void render() {
        if (graphics == null || grid == null) {
            return;
        }

        graphics.clearRect(
                0,
                0,
                canvas.getWidth(),
                canvas.getHeight()
        );

        double gap = flakeSize >= 4 ? 1 : 0;
        double pixelSize = Math.max(1, flakeSize - gap);

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                if (grid[y][x]) {
                    graphics.setFill(colorFor(x, y));

                    graphics.fillRect(
                            x * flakeSize,
                            y * flakeSize,
                            pixelSize,
                            pixelSize
                    );
                }
            }
        }
    }

    private Color colorFor(int x, int y) {
        return switch (colorMode) {
            case "Branco" -> Color.WHITE;
            case "Verde" -> Color.LIMEGREEN;
            case "Roxo" -> Color.MEDIUMPURPLE;
            case "Multicolorido" ->
                    Color.hsb((x * 19.0 + y * 11.0) % 360, 0.75, 1.0);
            default -> Color.rgb(145, 220, 255);
        };
    }

    // --------------------------------------------------
    // PRÉ-VISUALIZAÇÃO
    // --------------------------------------------------

    private void previewSnow() {
        applySettings();

        if (previewTimer != null) {
            previewTimer.stop();
        }

        previewing = true;
        snowing = true;

        clearGrid();

        simulationTimer = 0;
        spawnTimer = 0;
        lastFrame = 0;

        overlayStage.show();
        overlayStage.toFront();

        previewTimer = new Timeline(
                new KeyFrame(
                        Duration.seconds(PREVIEW_SECONDS),
                        event -> {
                            previewing = false;
                            clearSnow();
                        }
                )
        );

        previewTimer.setCycleCount(1);
        previewTimer.playFromStart();
    }

    private void clearGrid() {
        if (grid == null) {
            return;
        }

        for (boolean[] row : grid) {
            Arrays.fill(row, false);
        }

        for (int[] row : windDirection) {
            Arrays.fill(row, 0);
        }

        render();
    }

    private void clearSnow() {
        snowing = false;

        simulationTimer = 0;
        spawnTimer = 0;
        lastFrame = 0;
        movementStep = 0;
        nextSpawnInterval = getNextSpawnInterval();

        clearGrid();

        if (overlayStage != null) {
            overlayStage.hide();
        }
    }

    // --------------------------------------------------
    // DETECÇÃO DE INATIVIDADE DO WINDOWS
    // --------------------------------------------------

    private void initializeIdleMonitor() {
        idleMonitor = new Timeline(
                new KeyFrame(
                        Duration.millis(500),
                        event -> checkIdle()
                )
        );

        idleMonitor.setCycleCount(Animation.INDEFINITE);
    }

    private void checkIdle() {
        if (!configured || previewing) {
            return;
        }

        long idleMillis = getIdleMillis();

        if (idleMillis < 0) {
            return;
        }

        boolean shouldSnow = idleMillis >= idleSeconds * 1000L;

        if (shouldSnow && !snowing) {
            clearGrid();

            snowing = true;
            simulationTimer = 0;
            spawnTimer = 0;
            lastFrame = 0;

            overlayStage.show();
        } else if (!shouldSnow && snowing) {
            clearSnow();
        }
    }

    private long getIdleMillis() {
        LASTINPUTINFO info = new LASTINPUTINFO();

        info.cbSize = Native.getNativeSize(LASTINPUTINFO.class);

        if (!User32.INSTANCE.GetLastInputInfo(info)) {
            return -1;
        }

        long currentTick = Integer.toUnsignedLong(
                Kernel32.INSTANCE.GetTickCount()
        );

        long lastInputTick = Integer.toUnsignedLong(info.dwTime);

        return Integer.toUnsignedLong(
                (int) (currentTick - lastInputTick)
        );
    }

    // --------------------------------------------------
    // ÍCONE NA BANDEJA DO WINDOWS
    // --------------------------------------------------

    private void initializeTray() {
        if (!SystemTray.isSupported() || trayIcon != null) {
            return;
        }

        try {
            systemTray = SystemTray.getSystemTray();

            BufferedImage image = new BufferedImage(
                    16,
                    16,
                    BufferedImage.TYPE_INT_ARGB
            );

            Graphics2D g = image.createGraphics();

            g.setColor(java.awt.Color.WHITE);
            g.fillOval(5, 1, 5, 5);
            g.fillOval(2, 7, 5, 5);
            g.fillOval(9, 8, 5, 5);

            g.dispose();

            PopupMenu popup = new PopupMenu();

            MenuItem settingsItem = new MenuItem("Configurações");
            settingsItem.addActionListener(event ->
                    Platform.runLater(this::showSettings)
            );

            MenuItem exitItem = new MenuItem("Sair");
            exitItem.addActionListener(event ->
                    Platform.runLater(this::shutdown)
            );

            popup.add(settingsItem);
            popup.addSeparator();
            popup.add(exitItem);

            trayIcon = new TrayIcon(image, "SnowJake", popup);
            trayIcon.setImageAutoSize(true);

            trayIcon.addActionListener(event ->
                    Platform.runLater(this::showSettings)
            );

            systemTray.add(trayIcon);

        } catch (AWTException exception) {
            trayIcon = null;
            System.err.println(
                    "Não foi possível criar o ícone na bandeja: "
                            + exception.getMessage()
            );
        }
    }

    private void showSettings() {
        settingsStage.show();
        settingsStage.toFront();
    }

    // --------------------------------------------------
    // ENCERRAMENTO E ERROS
    // --------------------------------------------------

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("SnowJake");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.initOwner(settingsStage);
        alert.showAndWait();
    }

    private void shutdown() {
        if (previewTimer != null) {
            previewTimer.stop();
        }

        if (idleMonitor != null) {
            idleMonitor.stop();
        }

        if (animationTimer != null) {
            animationTimer.stop();
        }

        if (trayIcon != null && systemTray != null) {
            systemTray.remove(trayIcon);
            trayIcon = null;
        }

        if (overlayStage != null) {
            overlayStage.close();
        }

        if (settingsStage != null) {
            settingsStage.close();
        }

        Platform.exit();
    }

    @Override
    public void stop() {
        if (previewTimer != null) {
            previewTimer.stop();
        }

        if (idleMonitor != null) {
            idleMonitor.stop();
        }

        if (animationTimer != null) {
            animationTimer.stop();
        }

        if (trayIcon != null && systemTray != null) {
            systemTray.remove(trayIcon);
            trayIcon = null;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}