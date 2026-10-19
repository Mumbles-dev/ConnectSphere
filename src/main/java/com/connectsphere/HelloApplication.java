package com.connectsphere;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

import com.connectsphere.dao.DatabaseManager;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
                HelloApplication.class.getResource("views/MainView.fxml"));
        Scene scene = new Scene(fxmlLoader. load(), 1100, 680);
        stage.setTitle("ConnectSphere");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        DatabaseManager.closeConnection();
    }
}
