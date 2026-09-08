package ps2gm.game.manager.fx;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ps2gm.game.manager.BackgroundTasks;
import ps2gm.game.manager.Console;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.PopsGameManager;

/**
 * Controller for {@code HashCheckerScreen.fxml} - the "MD5 Checker".
 *
 * Given a game file: shows its name, resolves the game ID off the FX thread, and on
 * "Start" streams the file through an MD5 digest on a background {@link Task} with the
 * progress bar bound to it. The window can't be closed while a hash is running.
 */
public class HashCheckerController implements FxScreens.StageAware {

    @FXML private TextField gameNameField;
    @FXML private TextField gameIdField;
    @FXML private Button startButton;
    @FXML private TextField md5Field;
    @FXML private ProgressBar progressBar;

    private Stage stage;
    private File gameFile;
    private volatile boolean hashing = false;

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(e -> {
            if (hashing) {
                e.consume();
                new Alert(Alert.AlertType.WARNING, "You cannot exit this window while a task is being performed.").showAndWait();
            }
        });
    }

    /** Called from the façade before the window is shown. */
    void setGameFile(File file) {
        this.gameFile = file;
        gameNameField.setText(" " + file.getName());
        resolveGameIdInBackground(file);
    }

    private void resolveGameIdInBackground(File file) {
        Task<String> idTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (PopsGameManager.getCurrentConsole() == Console.PS1) {
                    return GameListManager.getPS1GameIDFromVCD(file);
                }
                return GameListManager.getPS2GameIDFromArchive(file.getAbsolutePath());
            }
        };
        idTask.setOnSucceeded(e -> gameIdField.setText(idTask.getValue()));
        idTask.setOnFailed(e -> PopsGameManager.displayErrorMessageDebug(String.valueOf(idTask.getException())));
        runDaemon(idTask);
    }

    @FXML
    private void onStart() {
        if (gameFile == null || hashing) {
            return;
        }
        hashing = true;
        startButton.setDisable(true);
        md5Field.clear();

        Task<String> md5Task = new Task<>() {
            @Override
            protected String call() throws Exception {
                MessageDigest digest = MessageDigest.getInstance("MD5");
                long size = gameFile.length();
                long read = 0;
                byte[] buf = new byte[8192];
                try (InputStream in = Files.newInputStream(gameFile.toPath())) {
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        digest.update(buf, 0, n);
                        read += n;
                        updateProgress(read, size <= 0 ? read : size);
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (byte b : digest.digest()) {
                    sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
                }
                return sb.toString();
            }
        };

        progressBar.progressProperty().bind(md5Task.progressProperty());
        md5Task.setOnSucceeded(e -> finishHash(md5Task.getValue()));
        md5Task.setOnFailed(e -> {
            PopsGameManager.displayErrorMessageDebug(String.valueOf(md5Task.getException()));
            finishHash(null);
        });
        runDaemon(md5Task);
    }

    private void finishHash(String md5) {
        progressBar.progressProperty().unbind();
        if (md5 != null) {
            md5Field.setText(md5);
            progressBar.setProgress(1.0);
        } else {
            progressBar.setProgress(0.0);
        }
        hashing = false;
        startButton.setDisable(false);
    }

    private static void runDaemon(Task<?> task) {
        BackgroundTasks.runDaemon("fx-hash-checker", task);
    }
}
