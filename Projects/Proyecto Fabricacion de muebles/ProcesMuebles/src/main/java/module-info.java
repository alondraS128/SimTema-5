module com.alondrasystems.procesmuebles {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;

    opens com.alondrasystems.procesmuebles to javafx.fxml;
    exports com.alondrasystems.procesmuebles;
}