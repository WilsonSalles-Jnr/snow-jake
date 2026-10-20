module com.boreal.snowjake {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.sun.jna.platform;
    requires java.desktop;
    requires com.sun.jna;


    opens com.boreal.snowjake to javafx.fxml;
    exports com.boreal.snowjake;
}