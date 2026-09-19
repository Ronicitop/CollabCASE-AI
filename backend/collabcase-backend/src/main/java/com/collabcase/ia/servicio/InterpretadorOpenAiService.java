package com.collabcase.ia.servicio;

import com.collabcase.ia.dto.PlanIa;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class InterpretadorOpenAiService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String modelo;
    private final String baseUrl;

    public InterpretadorOpenAiService(
            ObjectMapper objectMapper,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model:gpt-5.6-luna}") String modelo,
            @Value("${openai.base-url:https://api.openai.com/v1/responses}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public PlanIa interpretar(
            String mensajeUsuario,
            ModeloCompletoResponse modeloActual
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar OPENAI_API_KEY para usar el chat con IA"
            );
        }

        try {
            ObjectNode cuerpo = objectMapper.createObjectNode();
            cuerpo.put("model", modelo);
            cuerpo.put("store", false);
            cuerpo.put("max_output_tokens", 1200);
            cuerpo.put("instructions", instruccionesSistema());

            String contexto = objectMapper.writeValueAsString(modeloActual);

            cuerpo.put(
                    "input",
                    "RESPONDE ÚNICAMENTE CON JSON VÁLIDO.\n\n"
                            + "MODELO UML ACTUAL:\n"
                            + contexto
                            + "\n\nSOLICITUD DEL USUARIO:\n"
                            + mensajeUsuario
            );

            ObjectNode text = objectMapper.createObjectNode();
            ObjectNode format = objectMapper.createObjectNode();
            format.put("type", "json_object");
            text.set("format", format);
            cuerpo.set("text", text);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    objectMapper.writeValueAsString(cuerpo)
                            )
                    )
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "OpenAI respondió HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }

            JsonNode raiz = objectMapper.readTree(response.body());
            String textoSalida = extraerTextoSalida(raiz);

            if (textoSalida == null || textoSalida.isBlank()) {
                throw new IllegalStateException(
                        "OpenAI no devolvió un plan de operaciones UML"
                );
            }

            PlanIa plan = objectMapper.readValue(
                    textoSalida,
                    PlanIa.class
            );

            if (plan.operaciones() == null) {
                return new PlanIa(
                        plan.mensaje(),
                        List.of()
                );
            }

            return plan;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "La solicitud a OpenAI fue interrumpida",
                    ex
            );
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "No se pudo comunicar con OpenAI",
                    ex
            );
        }
    }

    private String extraerTextoSalida(JsonNode raiz) {
        JsonNode output = raiz.get("output");

        if (output == null || !output.isArray()) {
            return null;
        }

        for (JsonNode item : output) {
            if (!"message".equals(texto(item, "type"))) {
                continue;
            }

            JsonNode content = item.get("content");
            if (content == null || !content.isArray()) {
                continue;
            }

            for (JsonNode parte : content) {
                if ("output_text".equals(texto(parte, "type"))) {
                    return texto(parte, "text");
                }
            }
        }

        return null;
    }

    private String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);

        if (valor == null || valor.isNull()) {
            return null;
        }

        return valor.asString();
    }

    private String instruccionesSistema() {
        return """
                Eres el intérprete de comandos UML de CollabCASE AI.
                Devuelve únicamente un objeto JSON válido con esta forma:

                {
                  "mensaje": "resumen breve para el usuario",
                  "operaciones": [
                    {
                      "tipo": "CREAR_CLASE | RENOMBRAR_CLASE | ELIMINAR_CLASE | CREAR_ATRIBUTO | ACTUALIZAR_ATRIBUTO | ELIMINAR_ATRIBUTO | CREAR_RELACION | ACTUALIZAR_RELACION | ELIMINAR_RELACION",
                      "clase": null,
                      "nuevoNombre": null,
                      "atributo": null,
                      "nuevoNombreAtributo": null,
                      "tipoDato": null,
                      "permiteNulo": null,
                      "identificador": null,
                      "claseOrigen": null,
                      "claseDestino": null,
                      "tipoRelacion": null,
                      "multiplicidadOrigen": null,
                      "multiplicidadDestino": null,
                      "nombreRelacion": null,
                      "nuevoNombreRelacion": null
                    }
                  ]
                }

                Reglas:
                - Nunca inventes UUID ni IDs internos.
                - Usa nombres exactos de elementos existentes cuando los modifiques.
                - Si el usuario crea una clase con atributos, genera primero CREAR_CLASE y luego CREAR_ATRIBUTO.
                - Para una relación sin tipo explícito usa ASOCIACION.
                - Las multiplicidades válidas usan formatos como 1, 0..1, * o 1..*.
                - No modifiques elementos que el usuario no pidió.
                - Si la solicitud es ambigua, imposible o no corresponde al modelado UML, devuelve operaciones vacías y explícalo en mensaje.
                - Para ACTUALIZAR_ATRIBUTO, atributo es el nombre actual y nuevoNombreAtributo solo se usa si cambia.
                - Para ACTUALIZAR_RELACION, nombreRelacion identifica la relación actual y nuevoNombreRelacion solo se usa si cambia.
                """;
    }
}
