package com.alondrasystems.procesmuebles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class BonitaClient {

    private static final String BASE = "http://localhost:8080/bonita";

    private final CookieManager cookies = new CookieManager();
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private String apiToken;

    public BonitaClient() {
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    public void login(String usuario, String clave) throws Exception {
        String body = "username=" + URLEncoder.encode(usuario, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(clave, StandardCharsets.UTF_8)
                + "&redirect=false";

        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/loginservice"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200 && resp.statusCode() != 204) {
            throw new RuntimeException("Login fallido. Código: " + resp.statusCode());
        }

        for (HttpCookie c : cookies.getCookieStore().getCookies()) {
            if ("X-Bonita-API-Token".equals(c.getName())) {
                apiToken = c.getValue();
            }
        }
    }

    public String buscarProcesoId(String nombre) throws Exception {
        String filtro = URLEncoder.encode("name=" + nombre, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(BASE + "/API/bpm/process?p=0&c=1&f=" + filtro))
                .GET()
                .build();

        HttpResponse<String> resp = http.send(
                req,
                HttpResponse.BodyHandlers.ofString()
        );

        JsonNode lista = json.readTree(resp.body());

        if (!lista.isArray() || lista.isEmpty()) {
            throw new RuntimeException("No se encontró el proceso: " + nombre);
        }

        return lista.get(0).get("id").asText();
    }
    public String crearCaso(String procesoId) throws Exception {

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(BASE + "/API/bpm/process/" + procesoId + "/instantiation"))
                .header("Content-Type", "application/json")
                .header("X-Bonita-API-Token", apiToken)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> resp = http.send(
                req,
                HttpResponse.BodyHandlers.ofString()
        );

        if (resp.statusCode() != 200 && resp.statusCode() != 201) {
            throw new RuntimeException(
                    "No se pudo crear el caso. Código: "
                            + resp.statusCode()
                            + " Respuesta: "
                            + resp.body()
            );
        }

        JsonNode resultado = json.readTree(resp.body());

        return resultado.get("caseId").asText();
    }
    public void mostrarTareasDelCaso(String casoId) throws Exception {

        String filtro = URLEncoder.encode(
                "caseId=" + casoId,
                StandardCharsets.UTF_8
        );

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(
                                BASE + "/API/bpm/humanTask?p=0&c=20&f=" + filtro
                        ))
                .header("X-Bonita-API-Token", apiToken)
                .GET()
                .build();

        HttpResponse<String> resp = http.send(
                req,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("Código HTTP: " + resp.statusCode());

        JsonNode lista = json.readTree(resp.body());

        System.out.println("Tareas del caso " + casoId + ":");

        for (JsonNode tarea : lista) {

            System.out.println("-----------------------------");
            System.out.println("ID de tarea: " + tarea.get("id").asText());
            System.out.println("Nombre: " + tarea.get("name").asText());
            System.out.println("Estado: " + tarea.get("state").asText());
            System.out.println("Caso: " + tarea.get("caseId").asText());
            System.out.println("Actor ID: " + tarea.get("actorId").asText());
        }
    }
    public void mostrarContratoTarea(String taskId) throws Exception {

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(
                                BASE + "/API/bpm/userTask/" + taskId + "/contract"
                        ))
                .header("X-Bonita-API-Token", apiToken)
                .GET()
                .build();

        HttpResponse<String> resp = http.send(
                req,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("Código HTTP contrato: " + resp.statusCode());
        System.out.println("Contrato de la tarea " + taskId + ":");
        System.out.println(resp.body());
    }
    public void mostrarUsuarioWalter() throws Exception {

        String filtro = URLEncoder.encode(
                "userName=walter.bates",
                StandardCharsets.UTF_8
        );

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(
                                BASE + "/API/identity/user?p=0&c=10&f=" + filtro
                        ))
                .header("X-Bonita-API-Token", apiToken)
                .GET()
                .build();

        HttpResponse<String> resp = http.send(
                req,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("Código HTTP usuario: " + resp.statusCode());

        JsonNode lista = json.readTree(resp.body());

        if (!lista.isArray() || lista.isEmpty()) {
            throw new RuntimeException("No se encontró a Walter Bates");
        }

        JsonNode usuario = lista.get(0);

        System.out.println("-----------------------------");
        System.out.println("Usuario: " + usuario.get("userName").asText());
        System.out.println("ID de Walter Bates: " + usuario.get("id").asText());
    }
    public void asignarTarea(String taskId, String usuarioId) throws Exception {

        String body = "{\"assigned_id\":\"" + usuarioId + "\"}";

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(BASE + "/API/bpm/userTask/" + taskId)
                )
                .header("Content-Type", "application/json")
                .header("X-Bonita-API-Token", apiToken)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(
                req,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("Código HTTP asignación: " + resp.statusCode());
        System.out.println("Respuesta: " + resp.body());
    }
    public void mostrarDetalleTarea(String taskId) throws Exception {

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(BASE + "/API/bpm/userTask/" + taskId)
                )
                .header("X-Bonita-API-Token", apiToken)
                .GET()
                .build();

        HttpResponse<String> resp = http.send(
                req,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("Código HTTP detalle: " + resp.statusCode());
        System.out.println("Detalle de la tarea " + taskId + ":");
        System.out.println(resp.body());
    }
    public void ejecutarInspeccion(String taskId, boolean resultado) throws Exception {

        String body = "{\"resultadoInspeccion\":" + resultado + "}";

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(BASE + "/API/bpm/userTask/" + taskId + "/execution")
                )
                .header("Content-Type", "application/json")
                .header("X-Bonita-API-Token", apiToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(
                req,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("Código HTTP ejecución: " + resp.statusCode());
        System.out.println("Respuesta ejecución: " + resp.body());

        if (resp.statusCode() != 200 && resp.statusCode() != 204) {
            throw new RuntimeException(
                    "No se pudo ejecutar Inspección. Código: "
                            + resp.statusCode()
                            + " Respuesta: "
                            + resp.body()
            );
        }
    }
    public void ejecutarTarea(String taskId, String body) throws Exception {

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(BASE + "/API/bpm/userTask/" + taskId + "/execution")
                )
                .header("Content-Type", "application/json")
                .header("X-Bonita-API-Token", apiToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(
                req,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("Código HTTP ejecución: " + resp.statusCode());
        System.out.println("Respuesta ejecución: " + resp.body());

        if (resp.statusCode() != 200 && resp.statusCode() != 204) {
            throw new RuntimeException(
                    "No se pudo ejecutar la tarea. Código: "
                            + resp.statusCode()
                            + " Respuesta: "
                            + resp.body()
            );
        }
    }
    public JsonNode obtenerTareaActual(String caseId) throws Exception {

        String filtro = URLEncoder.encode(
                "caseId=" + caseId,
                StandardCharsets.UTF_8
        );

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(
                                BASE + "/API/bpm/humanTask?p=0&c=10&f=" + filtro
                        )
                )
                .header("X-Bonita-API-Token", apiToken)
                .GET()
                .build();

        HttpResponse<String> resp = http.send(
                req,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("Código consulta tarea: " + resp.statusCode());
        System.out.println("Respuesta tareas: " + resp.body());

        if (resp.statusCode() != 200) {
            throw new RuntimeException(
                    "No se pudieron consultar las tareas. Código: "
                            + resp.statusCode()
                            + " Respuesta: "
                            + resp.body()
            );
        }

        JsonNode lista = json.readTree(resp.body());

        System.out.println("Cantidad de tareas encontradas: " + lista.size());

        if (!lista.isArray() || lista.isEmpty()) {
            return null;
        }

        for (JsonNode tarea : lista) {

            System.out.println(
                    "Tarea encontrada: "
                            + tarea.get("id").asText()
                            + " - "
                            + tarea.get("displayName").asText()
                            + " - "
                            + tarea.get("state").asText()
            );

            if ("ready".equalsIgnoreCase(tarea.get("state").asText())) {
                return tarea;
            }
        }

        return null;
    }
}