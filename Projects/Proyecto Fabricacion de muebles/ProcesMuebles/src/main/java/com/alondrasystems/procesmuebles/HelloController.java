package com.alondrasystems.procesmuebles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HelloController {

    private static final String USUARIO = "walter.bates";
    private static final String CLAVE = "bpm";
    private static final String USUARIO_ID = "4";
    private static final String NOMBRE_PROCESO = "ProcesMuebles";

    /** clave normalizada (sin acentos, minúsculas) -> etiqueta que se muestra */
    private static final Map<String, String> ETAPAS = new LinkedHashMap<>();
    static {
        ETAPAS.put("disenar", "Diseñar producto");
        ETAPAS.put("corte", "Corte");
        ETAPAS.put("ensamblaj", "Ensamblaje");
        ETAPAS.put("lijad", "Lijado");
        ETAPAS.put("tapizad", "Tapizado");
        ETAPAS.put("barnizad", "Barnizado");
        ETAPAS.put("inspeccion", "Inspección");
        ETAPAS.put("retrabajo", "Retrabajo");
        ETAPAS.put("gestion", "Gestión de pedidos");
        ETAPAS.put("empaque", "Empaque y almacén");
        ETAPAS.put("distribucion", "Distribución y entrega");
    }

    /** Etapas que se repiten cuando Inspección no cumple y se pasa por Retrabajo. */
    private static final List<String> REPROCESO =
            List.of("Corte", "Ensamblaje", "Lijado", "Tapizado", "Barnizado", "Inspección");

    private enum Tipo { DISENAR, INSPECCION, RETRABAJO, CONFIRMAR }

    private interface Trabajo {
        Runnable ejecutar() throws Exception;
    }

    @FXML private Label welcomeText;
    @FXML private Label tareaLabel;
    @FXML private ListView<String> etapasListView;
    @FXML private Node disenarBox;
    @FXML private TextField nombreField;
    @FXML private TextArea descripcionArea;
    @FXML private CheckBox inspeccionCheckBox;
    @FXML private CheckBox confirmarCheckBox;
    @FXML private Button enviarTareaButton;
    @FXML private Button iniciarButton;

    private final BonitaClient bonita = new BonitaClient();
    private final ObjectMapper json = new ObjectMapper();
    private final Set<String> completadas = new HashSet<>();

    private String casoId;
    private JsonNode tareaActual;
    private Tipo tipoActual;

    @FXML
    public void initialize() {
        etapasListView.setMouseTransparent(true);
        etapasListView.setFocusTraversable(false);
        refrescarEtapas(null);
    }

    // ------------------------------------------------------------ iniciar

    @FXML
    protected void onHelloButtonClick() {
        segundoPlano("Conectando con Bonita...", () -> {
            bonita.login(USUARIO, CLAVE);
            String procesoId = bonita.buscarProcesoId(NOMBRE_PROCESO);
            String nuevo = bonita.crearCaso(procesoId);
            JsonNode tarea = esperarTarea(nuevo, null);
            return () -> {
                casoId = nuevo;
                completadas.clear();
                tareaActual = tarea;
                welcomeText.setText("Caso " + nuevo + " en ejecución.");
                mostrarTarea();
            };
        });
    }

    // ------------------------------------------------------------- enviar

    @FXML
    protected void onEnviarTarea() {
        if (tareaActual == null || tipoActual == null) {
            return;
        }

        ObjectNode body = json.createObjectNode();

        if (tipoActual == Tipo.DISENAR) {
            String nombre = nombreField.getText().trim();
            String descripcion = descripcionArea.getText().trim();
            if (nombre.isEmpty() || descripcion.isEmpty()) {
                mostrarError("Nombre y Descripción son obligatorios.");
                return;
            }
            body.put("nombre", nombre);
            body.put("descripcion", descripcion);

        } else if (tipoActual == Tipo.INSPECCION) {
            body.put("resultadoInspeccion", inspeccionCheckBox.isSelected());

        } else if (tipoActual == Tipo.CONFIRMAR) {
            if (!confirmarCheckBox.isSelected()) {
                mostrarError("Marca la confirmación para continuar.");
                return;
            }
            body.put("confirmado", true);
        }
        // RETRABAJO: sin contrato, el cuerpo queda como {}

        enviar(body);
    }

    private void enviar(ObjectNode body) {
        final String idTarea = tareaActual.get("id").asText();
        final String etapa = etapaDe(tareaActual);
        final String caso = casoId;

        segundoPlano("Enviando " + etapa + "...", () -> {
            bonita.asignarTarea(idTarea, USUARIO_ID);
            bonita.ejecutarTarea(idTarea, json.writeValueAsString(body));
            JsonNode siguiente = esperarTarea(caso, idTarea);
            return () -> {
                completadas.add(etapa);
                tareaActual = siguiente;
                mostrarTarea();
            };
        });
    }

    /** Bonita crea la siguiente tarea de forma asíncrona, por eso se consulta varias veces. */
    private JsonNode esperarTarea(String caso, String idAnterior) throws Exception {
        for (int i = 0; i < 10; i++) {
            JsonNode t = bonita.obtenerTareaActual(caso);
            if (t != null && !t.get("id").asText().equals(idAnterior)) {
                return t;
            }
            Thread.sleep(400);
        }
        return null; // sin tareas pendientes: el caso terminó
    }

    // ----------------------------------------------------------- pantalla

    private void mostrarTarea() {
        mostrar(disenarBox, false);
        mostrar(inspeccionCheckBox, false);
        mostrar(confirmarCheckBox, false);
        mostrar(enviarTareaButton, false);
        iniciarButton.setDisable(tareaActual != null);

        if (tareaActual == null) {
            tipoActual = null;
            tareaLabel.setText("Tarea actual: ninguna");
            if (casoId != null) {
                welcomeText.setText("El caso " + casoId + " terminó.");
                for (String etapa : ETAPAS.values()) {
                    if (!etapa.equals("Retrabajo")) {
                        completadas.add(etapa);
                    }
                }
            }
            refrescarEtapas(null);
            return;
        }

        String etapa = etapaDe(tareaActual);
        if (etapa.equals("Retrabajo")) {
            completadas.removeAll(REPROCESO); // el producto vuelve a pasar por Corte
        }
        refrescarEtapas(etapa);
        welcomeText.setText("Caso " + casoId + " en ejecución.");
        tareaLabel.setText("Tarea actual: " + tareaActual.get("displayName").asText());

        String clave = normalizar(tareaActual.get("name").asText());
        if (clave.contains("disenar")) {
            tipoActual = Tipo.DISENAR;
            nombreField.clear();
            descripcionArea.clear();
            mostrar(disenarBox, true);
            enviarTareaButton.setText("Enviar diseño");
        } else if (clave.contains("inspeccion")) {
            tipoActual = Tipo.INSPECCION;
            inspeccionCheckBox.setSelected(false);
            mostrar(inspeccionCheckBox, true);
            enviarTareaButton.setText("Enviar inspección");
        } else if (clave.contains("retrabajo")) {
            tipoActual = Tipo.RETRABAJO;
            enviarTareaButton.setText("Retrabajo terminado");
        } else {
            tipoActual = Tipo.CONFIRMAR;
            confirmarCheckBox.setSelected(false);
            mostrar(confirmarCheckBox, true);
            enviarTareaButton.setText("Confirmar y continuar");
        }
        mostrar(enviarTareaButton, true);
    }

    private void refrescarEtapas(String actual) {
        List<String> filas = new ArrayList<>();
        for (String etapa : ETAPAS.values()) {
            if (etapa.equals(actual)) {
                filas.add("🟡 " + etapa + "  ← ACTUAL");
            } else if (completadas.contains(etapa)) {
                filas.add("✅ " + etapa);
            } else {
                filas.add("⏳ " + etapa);
            }
        }
        etapasListView.getItems().setAll(filas);
    }

    private String etapaDe(JsonNode tarea) {
        String clave = normalizar(tarea.get("name").asText());
        for (Map.Entry<String, String> e : ETAPAS.entrySet()) {
            if (clave.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return tarea.get("displayName").asText();
    }

    // ---------------------------------------------------------- utilidades

    /** Corre el trabajo fuera del hilo de JavaFX y aplica el resultado en el hilo de la interfaz. */
    private void segundoPlano(String mensaje, Trabajo trabajo) {
        setOcupado(true, mensaje);
        Thread t = new Thread(() -> {
            try {
                Runnable alTerminar = trabajo.ejecutar();
                Platform.runLater(() -> {
                    setOcupado(false, null);
                    if (alTerminar != null) {
                        alTerminar.run();
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    setOcupado(false, null);
                    mostrarError(ex.getMessage());
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void setOcupado(boolean ocupado, String mensaje) {
        enviarTareaButton.setDisable(ocupado);
        iniciarButton.setDisable(ocupado || tareaActual != null);
        nombreField.setDisable(ocupado);
        descripcionArea.setDisable(ocupado);
        inspeccionCheckBox.setDisable(ocupado);
        confirmarCheckBox.setDisable(ocupado);
        if (mensaje != null) {
            welcomeText.setText(mensaje);
        }
    }

    private void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private void mostrarError(String mensaje) {
        new Alert(Alert.AlertType.ERROR,
                mensaje == null ? "Error desconocido" : mensaje).showAndWait();
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
    }
}