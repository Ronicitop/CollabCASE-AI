package com.collabcase.colaboracion.controlador;

import com.collabcase.colaboracion.servicio.ChatProyectoService;
import com.collabcase.colaboracion.dominio.SesionColaborativa;
import com.collabcase.colaboracion.dto.MensajeSalaDTO;
import com.collabcase.colaboracion.repositorio.SesionColaborativaRepository;
import com.collabcase.ia.servicio.ServicioIaPar;
import com.collabcase.modelado.dominio.AtributoDiagrama;
import com.collabcase.modelado.dominio.ClaseDiagrama;
import com.collabcase.modelado.dominio.ModeloDiagrama;
import com.collabcase.modelado.dominio.RelacionDiagrama;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import com.collabcase.modelado.repositorio.AtributoDiagramaRepository;
import com.collabcase.modelado.repositorio.ClaseDiagramaRepository;
import com.collabcase.modelado.repositorio.RelacionDiagramaRepository;
import com.collabcase.modelado.servicio.ModeloDiagramaService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/sala")
@CrossOrigin(origins = "*")
public class SalaColaborativaController {

    private final com.collabcase.notificacion.NotificacionesService notificaciones;
    private final ChatProyectoService chat;
    private final SimpMessagingTemplate messagingTemplate;
    private final ServicioIaPar servicioIaPar;
    private final ModeloDiagramaService modeloDiagramaService;
    private final SesionColaborativaRepository sesionRepository;
    private final ClaseDiagramaRepository claseRepository;
    private final AtributoDiagramaRepository atributoRepository;
    private final RelacionDiagramaRepository relacionRepository;

    public SalaColaborativaController(com.collabcase.notificacion.NotificacionesService notificaciones,
                                      ChatProyectoService chat,
                                      SimpMessagingTemplate messagingTemplate,
                                      ServicioIaPar servicioIaPar,
                                      ModeloDiagramaService modeloDiagramaService,
                                      SesionColaborativaRepository sesionRepository,
                                      ClaseDiagramaRepository claseRepository,
                                      AtributoDiagramaRepository atributoRepository,
                                      RelacionDiagramaRepository relacionRepository) {
        this.notificaciones = notificaciones;
        this.chat = chat;
        this.messagingTemplate = messagingTemplate;
        this.servicioIaPar = servicioIaPar;
        this.modeloDiagramaService = modeloDiagramaService;
        this.sesionRepository = sesionRepository;
        this.claseRepository = claseRepository;
        this.atributoRepository = atributoRepository;
        this.relacionRepository = relacionRepository;
    }

    @GetMapping("/proyecto/{proyectoId}/mensajes")
    public ResponseEntity<List<MensajeSalaDTO>> obtenerHistorialProyecto(@PathVariable UUID proyectoId) {
        return ResponseEntity.ok(chat.historialProyecto(proyectoId));
    }

    @GetMapping("/{sesionId}/mensajes")
    public ResponseEntity<List<MensajeSalaDTO>> obtenerHistorial(@PathVariable UUID sesionId) {
        return ResponseEntity.ok(chat.historialSesion(sesionId));
    }

    @MessageMapping("/sesion/{sesionId}/enviar-mensaje")
    public void recibirYTransmitirMensaje(@DestinationVariable UUID sesionId, MensajeSalaDTO dto) {
        procesarMensajeEntrante(sesionId, dto);
    }

    @PostMapping("/{sesionId}/mensaje")
    public ResponseEntity<MensajeSalaDTO> recibirMensajeRest(@PathVariable UUID sesionId, @RequestBody MensajeSalaDTO dto) {
        return ResponseEntity.ok(procesarMensajeEntrante(sesionId, dto));
    }

    private MensajeSalaDTO procesarMensajeEntrante(UUID sesionId, MensajeSalaDTO dto) {
        MensajeSalaDTO guardado = chat.guardar(sesionId, dto);
        if ("USUARIO".equals(guardado.getTipoEmisor()) &&
                (guardado.getContenido().toLowerCase().contains("@ia") || guardado.getContenido().toLowerCase().startsWith("ia:"))) {
            new Thread(() -> {
                String respuestaTexto = servicioIaPar.procesarConsultaEnSala(sesionId, guardado.getRemitenteNombre(), guardado.getContenido());
                chat.guardar(sesionId, new MensajeSalaDTO(null, sesionId, "CollabCASE AI", "IA", respuestaTexto, null));
                aplicarOperacionesSiExisten(sesionId, respuestaTexto);
            }, "collabcase-chat-ia").start();
        }
        return guardado;
    }

    private void aplicarOperacionesSiExisten(UUID sesionId, String textoRespuesta) {
        try {
            Pattern blockPattern = Pattern.compile("<<<OPERACIONES_CANONICAS(.*?)OPERACIONES_CANONICAS>>>", Pattern.DOTALL);
            Matcher blockMatcher = blockPattern.matcher(textoRespuesta);

            if (!blockMatcher.find()) {
                return;
            }

            String bloqueJson = blockMatcher.group(1).trim();

            Optional<SesionColaborativa> sesionOpt = sesionRepository.findById(sesionId);
            if (sesionOpt.isEmpty() || sesionOpt.get().getProyecto() == null) {
                return;
            }

            SesionColaborativa sesion = sesionOpt.get();
            UUID proyectoId = sesion.getProyecto().getId();
            ModeloDiagrama modelo = modeloDiagramaService.buscarPorProyecto(proyectoId);

            boolean huboCambios = false;

            // 1. Extraer y crear clases (soporta cualquier clase enviada por la IA: Notificacion, Cliente, etc.)
            Pattern classPattern = Pattern.compile("\"tipo\"\\s*:\\s*\"CREATE_CLASS\"[^\"]*\"nombre\"\\s*:\\s*\"([^\"]+)\"");
            Matcher classMatcher = classPattern.matcher(bloqueJson);

            while (classMatcher.find()) {
                String nombreClase = classMatcher.group(1).trim();
                List<ClaseDiagrama> clasesActuales = claseRepository.findByModeloId(modelo.getId());
                boolean yaExiste = clasesActuales.stream().anyMatch(c -> c.getNombre().equalsIgnoreCase(nombreClase));

                if (!yaExiste) {
                    ClaseDiagrama nuevaClase = new ClaseDiagrama();
                    nuevaClase.setModelo(modelo);
                    nuevaClase.setNombre(nombreClase);
                    nuevaClase.setPosicionX(650.0);
                    nuevaClase.setPosicionY(180.0);
                    ClaseDiagrama claseGuardada = claseRepository.save(nuevaClase);

                    // Atributo id por defecto si no viene
                    AtributoDiagrama attrId = new AtributoDiagrama();
                    attrId.setClase(claseGuardada);
                    attrId.setNombre("id");
                    attrId.setTipoDato("Long");
                    attrId.setIdentificador(true);
                    attrId.setPermiteNulo(false);
                    atributoRepository.save(attrId);

                    // Extraer atributos definidos en el payload
                    Pattern attrPattern = Pattern.compile("\"nombre\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"tipoDato\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher attrMatcher = attrPattern.matcher(bloqueJson);
                    while (attrMatcher.find()) {
                        String attrNom = attrMatcher.group(1).trim();
                        String attrTipo = attrMatcher.group(2).trim();
                        if (!"id".equalsIgnoreCase(attrNom)) {
                            AtributoDiagrama extraAttr = new AtributoDiagrama();
                            extraAttr.setClase(claseGuardada);
                            extraAttr.setNombre(attrNom);
                            extraAttr.setTipoDato(attrTipo);
                            extraAttr.setIdentificador(false);
                            extraAttr.setPermiteNulo(false);
                            atributoRepository.save(extraAttr);
                        }
                    }
                    huboCambios = true;
                }
            }

            // 2. Extraer y crear relaciones de asociación
            Pattern relPattern = Pattern.compile("\"tipo\"\\s*:\\s*\"CREATE_RELATION\"[^\"]*\"origen\"\\s*:\\s*\"([^\"]+)\"[^\"]*\"destino\"\\s*:\\s*\"([^\"]+)\"");
            Matcher relMatcher = relPattern.matcher(bloqueJson);

            while (relMatcher.find()) {
                String origenNom = relMatcher.group(1).trim();
                String destinoNom = relMatcher.group(2).trim();

                List<ClaseDiagrama> clases = claseRepository.findByModeloId(modelo.getId());
                Optional<ClaseDiagrama> origenOpt = clases.stream().filter(c -> c.getNombre().equalsIgnoreCase(origenNom)).findFirst();
                Optional<ClaseDiagrama> destinoOpt = clases.stream().filter(c -> c.getNombre().equalsIgnoreCase(destinoNom)).findFirst();

                if (origenOpt.isPresent() && destinoOpt.isPresent()) {
                    ClaseDiagrama origen = origenOpt.get();
                    ClaseDiagrama destino = destinoOpt.get();

                    List<RelacionDiagrama> relaciones = relacionRepository.findByModeloId(modelo.getId());
                    boolean existeRel = relaciones.stream().anyMatch(r ->
                            (r.getClaseOrigen().getId().equals(origen.getId()) && r.getClaseDestino().getId().equals(destino.getId())) ||
                            (r.getClaseOrigen().getId().equals(destino.getId()) && r.getClaseDestino().getId().equals(origen.getId()))
                    );

                    if (!existeRel) {
                        RelacionDiagrama rel = new RelacionDiagrama();
                        rel.setModelo(modelo);
                        rel.setClaseOrigen(origen);
                        rel.setClaseDestino(destino);
                        rel.setTipo("ASOCIACION");
                        rel.setNombre("asociada");
                        rel.setMultiplicidadOrigen("1..1");
                        rel.setMultiplicidadDestino("0..*");
                        relacionRepository.save(rel);
                        huboCambios = true;
                    }
                }
            }

            // 3. Emitir a los canales suscritos por App.tsx
            if (huboCambios) {
                notificaciones.modelo(proyectoId);
                ModeloCompletoResponse modeloActualizado = modeloDiagramaService.obtenerModeloCompleto(proyectoId);
                messagingTemplate.convertAndSend("/topic/sesiones/" + sesion.getCodigo() + "/modelo", modeloActualizado);
                messagingTemplate.convertAndSend("/topic/sesiones/" + sesion.getCodigo() + "/operaciones", "MODELO_ACTUALIZADO");
                messagingTemplate.convertAndSend("/topic/sesion/" + sesionId + "/operaciones", "MODELO_ACTUALIZADO");
            }

        } catch (Exception e) {
            System.err.println("Aviso aplicando operaciones de IA al lienzo: " + e.getMessage());
        }
    }
}
