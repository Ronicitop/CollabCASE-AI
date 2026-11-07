package com.collabcase.ia.servicio;

import com.collabcase.colaboracion.dominio.AuditoriaOperacion;
import com.collabcase.colaboracion.dominio.MensajeSala;
import com.collabcase.colaboracion.repositorio.AuditoriaOperacionRepository;
import com.collabcase.colaboracion.repositorio.MensajeSalaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ServicioIaParImpl implements ServicioIaPar {

    @Value("${ia.motor.activo:CLOUD}")
    private String motorActivo;

    @Value("${ia.cloud.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ia.cloud.gemini.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent}")
    private String geminiUrl;

    @Value("${ia.local.ollama.base-url:http://localhost:11434/api/generate}")
    private String ollamaUrl;

    @Value("${ia.local.ollama.model:llama3.2:1b}")
    private String ollamaModel;

    private final MensajeSalaRepository mensajeSalaRepository;
    private final AuditoriaOperacionRepository auditoriaRepository;
    private final RestTemplate restTemplate;

    public ServicioIaParImpl(MensajeSalaRepository mensajeSalaRepository,
                             AuditoriaOperacionRepository auditoriaRepository) {
        this.mensajeSalaRepository = mensajeSalaRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String procesarConsultaEnSala(UUID sesionId, String remitenteNombre, String consulta) {
        List<MensajeSala> mensajesPrevios = mensajeSalaRepository.findBySesionIdOrderByFechaHoraAsc(sesionId);
        List<AuditoriaOperacion> ultimosCambios = auditoriaRepository.findTop15BySesionIdOrderByFechaHoraDesc(sesionId);

        String contextoHistorial = mensajesPrevios.stream()
                .map(m -> m.getRemitenteNombre() + ": " + m.getContenido())
                .collect(Collectors.joining("\n"));

        String contextoAuditoria = ultimosCambios.stream()
                .map(a -> "[" + a.getTipoOperacion() + "] por " + a.getUsuarioNombre() + ": " + a.getDescripcion())
                .collect(Collectors.joining("\n"));

        StringBuilder sb = new StringBuilder();
        sb.append("Eres el Agente de IA Par de CollabCASE AI, un colega arquitecto de software experto en UML 2.5 y generacion Spring Boot bajo PUDS.\n\n");
        sb.append("HISTORIAL RECIENTE DE LA SALA:\n");
        sb.append(contextoHistorial.isBlank() ? "Sin mensajes previos.\n\n" : contextoHistorial + "\n\n");
        sb.append("ULTIMOS CAMBIOS REALIZADOS:\n");
        sb.append(contextoAuditoria.isBlank() ? "Sin cambios registrados aun.\n\n" : contextoAuditoria + "\n\n");
        sb.append("CONSULTA O INSTRUCCIÓN DE ").append(remitenteNombre).append(":\n");
        sb.append(consulta).append("\n\n");
        sb.append("REGLAS ESTRICTAS DE RESPUESTA:\n");
        sb.append("1. Si la consulta pide solo teoria, explicacion o sugerencias, responde en texto tecnico conciso y NO generes operaciones sobre el lienzo.\n");
        sb.append("2. Si la consulta ordena explicitamente crear, dibujar o aplicar cambios (ej: 'dibujalo', 'crea la clase', 'agrega la relacion'), responde con un parrafo de explicacion Y AL FINAL incluye un bloque JSON con este formato exacto:\n");
        sb.append("<<<OPERACIONES_CANONICAS\n");
        sb.append("[\n");
        sb.append("  {\"tipo\": \"CREATE_CLASS\", \"nombre\": \"Cliente\", \"x\": 350, \"y\": 150, \"atributos\": [{\"nombre\": \"nombre\", \"tipoDato\": \"String\"}, {\"nombre\": \"email\", \"tipoDato\": \"String\"}]},\n");
        sb.append("  {\"tipo\": \"CREATE_RELATION\", \"origen\": \"Prestamo\", \"destino\": \"Cliente\", \"tipoRelacion\": \"ASOCIACION\", \"multOrigen\": \"*\", \"multDestino\": \"1\"}\n");
        sb.append("]\n");
        sb.append("OPERACIONES_CANONICAS>>>\n");

        String promptCompleto = sb.toString();

        if ("LOCAL".equalsIgnoreCase(motorActivo)) {
            return invocarOllamaLocal(promptCompleto);
        } else {
            return invocarGeminiCloud(promptCompleto);
        }
    }

    @SuppressWarnings("rawtypes")
    private String invocarGeminiCloud(String prompt) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return "CollabCASE IA: Clave de Gemini no configurada en el servidor. Por favor define GEMINI_API_KEY o cambia a motor LOCAL.";
        }
        try {
            String endpoint = geminiUrl + "?key=" + geminiApiKey;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", Collections.singletonList(part));

            Map<String, Object> body = new HashMap<>();
            body.put("contents", Collections.singletonList(content));

            HttpEntity<Object> reqEntity = new HttpEntity<>(body, headers);
            ResponseEntity response = restTemplate.postForEntity(endpoint, reqEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map respMap = (Map) response.getBody();
                List candidates = (List) respMap.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map firstCandidate = (Map) candidates.get(0);
                    Map contentObj = (Map) firstCandidate.get("content");
                    List parts = (List) contentObj.get("parts");
                    Map firstPart = (Map) parts.get(0);
                    return String.valueOf(firstPart.get("text"));
                }
            }
            return "CollabCASE IA: No se obtuvo respuesta estructurada del motor Cloud.";
        } catch (Exception e) {
            return "CollabCASE IA (Error Cloud): " + e.getMessage();
        }
    }

    @SuppressWarnings("rawtypes")
    private String invocarOllamaLocal(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("model", ollamaModel);
            body.put("prompt", prompt);
            body.put("stream", false);

            HttpEntity<Object> reqEntity = new HttpEntity<>(body, headers);
            ResponseEntity response = restTemplate.postForEntity(ollamaUrl, reqEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map respMap = (Map) response.getBody();
                return String.valueOf(respMap.get("response"));
            }
            return "CollabCASE IA: El modelo local de Ollama no devolvio contenido.";
        } catch (Exception e) {
            return "CollabCASE IA (Error Local): No se pudo conectar a Ollama en " + ollamaUrl + ".";
        }
    }
}