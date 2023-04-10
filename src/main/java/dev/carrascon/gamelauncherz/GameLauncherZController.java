package dev.carrascon.gamelauncherz;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.stream.Stream;

public class GameLauncherZController implements Initializable {
    @FXML
    private VBox gameListContainer;
    @FXML
    private Button selectFolderButton;

    @FXML
    private Button selectShortcutsFolderButton;

    private static final String PLAYTIME_FILE = "playtime.properties";
    private static final String SETTINGS_FILE = "settings.properties";
    private static final String GAMES_FOLDER_KEY = "gamesFolder";
    private static final String SHORTCUTS_FOLDER_KEY = "shortcutsFolder";
    private String shortcutsFolderPath;

    private String gamesFolderPath;
    private Map<String, Long> playtimeMap = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadPlaytimeData();
        loadSettings();
        if (gamesFolderPath != null) {
            loadGameButtons();
        } else if (shortcutsFolderPath != null) {
            loadShortcutButtons();
        }
        selectFolderButton.setOnAction(event -> openDirectoryChooser(false));
        selectShortcutsFolderButton.setOnAction(event -> openDirectoryChooser(true));
    }


    private void openDirectoryChooser(boolean usingGameFolder) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(usingGameFolder ? "Select Game Folder" : "Select Shortcuts Folder");
        Stage stage = (Stage) selectFolderButton.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory != null) {
            if (usingGameFolder) {
                gamesFolderPath = selectedDirectory.getAbsolutePath();
            } else {
                shortcutsFolderPath = selectedDirectory.getAbsolutePath();
            }
            saveSettings();
            if (usingGameFolder) {
                loadGameButtons();
            } else {
                loadShortcutButtons();
            }
        }
    }


    private void loadShortcutButtons() {
        if (shortcutsFolderPath == null) {
            return;
        }

        gameListContainer.getChildren().clear();

        try (Stream<Path> paths = Files.list(Paths.get(shortcutsFolderPath))) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".lnk"))
                    .forEach(p -> createGameButton(p.getFileName().toString(), false));
        } catch (IOException e) {
            System.err.println("Error loading shortcut buttons: " + e.getMessage());
        }
    }

    private void loadPlaytimeData() {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(PLAYTIME_FILE)) {
            properties.load(fis);
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("path.")) {
                    if (key.equals("path.games")) {
                        gamesFolderPath = properties.getProperty(key);
                    } else if (key.equals("path.shortcuts")) {
                        shortcutsFolderPath = properties.getProperty(key);
                    }
                } else {
                    playtimeMap.put(key, Long.parseLong(properties.getProperty(key)));
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading playtime data: " + e.getMessage());
        }
    }

    private void loadSettings() {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(SETTINGS_FILE)) {
            properties.load(fis);
            gamesFolderPath = properties.getProperty(GAMES_FOLDER_KEY);
            shortcutsFolderPath = properties.getProperty(SHORTCUTS_FOLDER_KEY);
        } catch (IOException e) {
            System.err.println("Error loading settings: " + e.getMessage());
        }
    }

    private void saveSettings() {
        Properties properties = new Properties();
        if (gamesFolderPath != null) {
            properties.setProperty(GAMES_FOLDER_KEY, gamesFolderPath);
        }
        if (shortcutsFolderPath != null) {
            properties.setProperty(SHORTCUTS_FOLDER_KEY, shortcutsFolderPath);
        }
        try (FileOutputStream fos = new FileOutputStream(SETTINGS_FILE)) {
            properties.store(fos, "Settings");
        } catch (IOException e) {
            System.err.println("Error saving settings: " + e.getMessage());
        }
    }

    private void savePlaytimeData() {
        Properties properties = new Properties();
        for (Map.Entry<String, Long> entry : playtimeMap.entrySet()) {
            properties.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        if (gamesFolderPath != null) {
            properties.setProperty("path.games", gamesFolderPath);
        }
        if (shortcutsFolderPath != null) {
            properties.setProperty("path.shortcuts", shortcutsFolderPath);
        }
        try (FileOutputStream fos = new FileOutputStream(PLAYTIME_FILE)) {
            properties.store(fos, "Playtime data");
        } catch (IOException e) {
            System.err.println("Error saving playtime data: " + e.getMessage());
        }
    }

    private void loadGameButtons() {
        if (gamesFolderPath == null) {
            return;
        }

        gameListContainer.getChildren().clear();

        try (Stream<Path> folders = Files.list(Paths.get(gamesFolderPath))) {
            folders.filter(Files::isDirectory)
                    .forEach(folder -> {
                        try (Stream<Path> files = Files.list(folder)) {
                            files.filter(Files::isRegularFile)
                                    .filter(file -> {
                                        String fileName = file.getFileName().toString();
                                        return fileName.endsWith(".exe") && !fileName.startsWith("Unity") && !fileName.startsWith("unins");
                                    })
                                    .forEach(file -> createGameButton(file.getFileName().toString(), true));
                        } catch (IOException e) {
                            System.err.println("Error loading game buttons: " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error loading game buttons: " + e.getMessage());
        }
    }



    private void createGameButton(String gameExecutable, boolean usingGameFolder) {
        long playtime = playtimeMap.getOrDefault(gameExecutable, 0L);
        String buttonText = String.format("%s (Playtime: %d min)", gameExecutable, playtime / 60000);
        Button gameButton = new Button(buttonText);
        gameButton.setOnAction(event -> launchGame(gameExecutable, usingGameFolder));
        gameListContainer.getChildren().add(gameButton);
    }

    private void launchGame(String gameExecutable, boolean usingGameFolder) {
        if (gamesFolderPath == null && shortcutsFolderPath == null) {
            return;
        }

        try {
            File gameFile = new File((usingGameFolder ? gamesFolderPath : shortcutsFolderPath) + File.separator + gameExecutable);

            long startTime = System.currentTimeMillis();

            ProcessBuilder processBuilder = new ProcessBuilder(gameFile.getAbsolutePath());
            Process process = processBuilder.start();
            process.waitFor();

            long endTime = System.currentTimeMillis();
            long playtime = endTime - startTime;
            playtimeMap.put(gameExecutable, playtimeMap.getOrDefault(gameExecutable, 0L) + playtime);
            savePlaytimeData();

            // Refresh the game buttons to show updated playtime
            if (usingGameFolder) {
                loadGameButtons();
            } else {
                loadShortcutButtons();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error launching game: " + gameExecutable + ", " + e.getMessage());
        }
    }

}
