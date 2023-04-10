module dev.carrascon.gamelauncherz {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;

    opens dev.carrascon.gamelauncherz to javafx.fxml;
    exports dev.carrascon.gamelauncherz;
}