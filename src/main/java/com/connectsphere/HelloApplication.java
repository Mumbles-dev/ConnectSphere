package com.connectsphere;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import javafx.geometry.Rectangle2D;
import java.io.IOException;

import com.connectsphere.dao.DatabaseManager;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
                HelloApplication.class.getResource("views/MainView.fxml"));
        Scene scene = new Scene(fxmlLoader. load(), 1100, 680);
        stage.setTitle("ConnectSphere");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/com/connectsphere/images/Logo.png")));
        stage.setScene(scene);
        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        stage.setMinWidth(Math.min(1040, vb.getWidth()));
        stage.setMinHeight(Math.min(640, vb.getHeight()));
        stage.setWidth(Math.min(1280, vb.getWidth()));
        stage.setHeight(Math.min(820, vb.getHeight()));
        stage.show();
    }

    @Override
    public void stop() {
        DatabaseManager.closeConnection();
    }
}
