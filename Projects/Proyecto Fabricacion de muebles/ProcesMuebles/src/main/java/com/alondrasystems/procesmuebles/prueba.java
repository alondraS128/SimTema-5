package com.alondrasystems.procesmuebles;

import com.fasterxml.jackson.databind.JsonNode;

public class prueba {

    public static void main(String[] args) throws Exception {

        BonitaClient bonita = new BonitaClient();

        bonita.login("walter.bates", "bpm");

        JsonNode tarea = bonita.obtenerTareaActual("3004");

        if (tarea == null) {

            System.out.println("No hay tareas pendientes.");

        } else {

            System.out.println("-----------------------------");
            System.out.println("ID de tarea: " + tarea.get("id").asText());
            System.out.println("Nombre: " + tarea.get("displayName").asText());
            System.out.println("Estado: " + tarea.get("state").asText());
            System.out.println("Caso: " + tarea.get("caseId").asText());
        }
    }
}