package com.collabcase.modelado.servicio;

import com.collabcase.modelado.dominio.AtributoDiagrama;
import com.collabcase.modelado.dominio.ClaseDiagrama;
import com.collabcase.modelado.dominio.ModeloDiagrama;
import com.collabcase.modelado.dominio.RelacionDiagrama;
import com.collabcase.modelado.dto.AtributoDiagramaRequest;
import com.collabcase.modelado.dto.AtributoDiagramaResponse;
import com.collabcase.modelado.dto.ClaseDiagramaRequest;
import com.collabcase.modelado.dto.ClaseDiagramaResponse;
import com.collabcase.modelado.dto.ModeloCompletoRequest;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import com.collabcase.modelado.dto.RelacionDiagramaRequest;
import com.collabcase.modelado.dto.RelacionDiagramaResponse;
import com.collabcase.modelado.repositorio.AtributoDiagramaRepository;
import com.collabcase.modelado.repositorio.ClaseDiagramaRepository;
import com.collabcase.modelado.repositorio.ModeloDiagramaRepository;
import com.collabcase.modelado.repositorio.RelacionDiagramaRepository;
import com.collabcase.proyecto.dominio.Proyecto;
import com.collabcase.proyecto.repositorio.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ModeloDiagramaService {

    private final ModeloDiagramaRepository modeloDiagramaRepository;
    private final ProyectoRepository proyectoRepository;
    private final ClaseDiagramaRepository claseDiagramaRepository;
    private final AtributoDiagramaRepository atributoDiagramaRepository;
    private final RelacionDiagramaRepository relacionDiagramaRepository;

    public ModeloDiagramaService(
            ModeloDiagramaRepository modeloDiagramaRepository,
            ProyectoRepository proyectoRepository,
            ClaseDiagramaRepository claseDiagramaRepository,
            AtributoDiagramaRepository atributoDiagramaRepository,
            RelacionDiagramaRepository relacionDiagramaRepository) {

        this.modeloDiagramaRepository = modeloDiagramaRepository;
        this.proyectoRepository = proyectoRepository;
        this.claseDiagramaRepository = claseDiagramaRepository;
        this.atributoDiagramaRepository = atributoDiagramaRepository;
        this.relacionDiagramaRepository = relacionDiagramaRepository;
    }

    public ModeloDiagrama crearParaProyecto(UUID proyectoId) {

        if (modeloDiagramaRepository.findByProyectoId(proyectoId).isPresent()) {
            throw new IllegalStateException(
                    "El proyecto ya tiene un modelo de diagrama"
            );
        }

        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Proyecto no encontrado con id: " + proyectoId
                        )
                );

        ModeloDiagrama modelo = ModeloDiagrama.builder()
                .proyecto(proyecto)
                .version(1L)
                .build();

        return modeloDiagramaRepository.save(modelo);
    }

    public ModeloDiagrama buscarPorId(UUID id) {

        return modeloDiagramaRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Modelo de diagrama no encontrado con id: " + id
                        )
                );
    }

    public ModeloDiagrama buscarPorProyecto(UUID proyectoId) {

        return modeloDiagramaRepository.findByProyectoId(proyectoId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "El proyecto no tiene un modelo de diagrama"
                        )
                );
    }

    public ModeloCompletoResponse obtenerModeloCompleto(UUID proyectoId) {

        ModeloDiagrama modelo = buscarPorProyecto(proyectoId);

        List<ClaseDiagrama> clases =
                claseDiagramaRepository.findByModeloId(modelo.getId());

        List<ClaseDiagramaResponse> clasesResponse = clases.stream()
                .map(clase -> {

                    List<AtributoDiagrama> atributos =
                            atributoDiagramaRepository.findByClaseId(
                                    clase.getId()
                            );

                    List<AtributoDiagramaResponse> atributosResponse =
                            atributos.stream()
                                    .map(atributo ->
                                            new AtributoDiagramaResponse(
                                                    atributo.getId(),
                                                    atributo.getNombre(),
                                                    atributo.getTipoDato(),
                                                    atributo.getPermiteNulo(),
                                                    atributo.getIdentificador()
                                            )
                                    )
                                    .toList();

                    return new ClaseDiagramaResponse(
                            clase.getId(),
                            clase.getNombre(),
                            clase.getPosicionX(),
                            clase.getPosicionY(),
                            atributosResponse
                    );
                })
                .toList();

        List<RelacionDiagrama> relaciones =
                relacionDiagramaRepository.findByModeloId(modelo.getId());

        List<RelacionDiagramaResponse> relacionesResponse =
                relaciones.stream()
                        .map(relacion ->
                                new RelacionDiagramaResponse(
                                        relacion.getId(),
                                        relacion.getClaseOrigen().getId(),
                                        relacion.getClaseDestino().getId(),
                                        relacion.getTipo(),
                                        relacion.getMultiplicidadOrigen(),
                                        relacion.getMultiplicidadDestino(),
                                        relacion.getNombre(),
                                        relacion.getClaseAsociacion() == null
                                                ? null
                                                : relacion.getClaseAsociacion().getId()
                                )
                        )
                        .toList();

        return new ModeloCompletoResponse(
                modelo.getId(),
                modelo.getProyecto().getId(),
                modelo.getVersion(),
                modelo.getActualizadoEn(),
                clasesResponse,
                relacionesResponse
        );
    }

    @Transactional
    public ModeloCompletoResponse guardarModeloCompleto(
            UUID proyectoId,
            ModeloCompletoRequest request) {

        ModeloDiagrama modelo = buscarPorProyecto(proyectoId);

        Map<String, ClaseDiagrama> clasesPorClave = new HashMap<>();
        Set<UUID> clasesConservadas = new HashSet<>();

        for (ClaseDiagramaRequest claseRequest : request.clases()) {

            if (clasesPorClave.containsKey(claseRequest.claveCliente())) {
                throw new IllegalStateException(
                        "La claveCliente está repetida: "
                                + claseRequest.claveCliente()
                );
            }

            ClaseDiagrama clase;

            if (claseRequest.id() == null) {

                clase = ClaseDiagrama.builder()
                        .modelo(modelo)
                        .nombre(claseRequest.nombre())
                        .posicionX(claseRequest.posicionX())
                        .posicionY(claseRequest.posicionY())
                        .build();

            } else {

                clase = claseDiagramaRepository
                        .findById(claseRequest.id())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Clase no encontrada con id: "
                                                + claseRequest.id()
                                )
                        );

                if (!clase.getModelo().getId().equals(modelo.getId())) {
                    throw new IllegalStateException(
                            "La clase no pertenece al modelo actual"
                    );
                }

                clase.setNombre(claseRequest.nombre());
                clase.setPosicionX(claseRequest.posicionX());
                clase.setPosicionY(claseRequest.posicionY());
            }

            clase = claseDiagramaRepository.save(clase);

            clasesPorClave.put(
                    claseRequest.claveCliente(),
                    clase
            );

            clasesConservadas.add(clase.getId());

            sincronizarAtributos(
                    clase,
                    claseRequest.atributos()
            );
        }

        sincronizarRelaciones(
                modelo,
                request.relaciones(),
                clasesPorClave
        );

        eliminarClasesAusentes(
                modelo,
                clasesConservadas
        );

        modelo.setVersion(modelo.getVersion() + 1);

        modeloDiagramaRepository.saveAndFlush(modelo);

        return obtenerModeloCompleto(proyectoId);
    }

    private void sincronizarAtributos(
            ClaseDiagrama clase,
            List<AtributoDiagramaRequest> atributosRequest) {

        List<AtributoDiagrama> atributosExistentes =
                atributoDiagramaRepository.findByClaseId(clase.getId());

        Set<UUID> atributosConservados = new HashSet<>();

        for (AtributoDiagramaRequest request : atributosRequest) {

            AtributoDiagrama atributo;

            if (request.id() == null) {

                atributo = AtributoDiagrama.builder()
                        .clase(clase)
                        .nombre(request.nombre())
                        .tipoDato(request.tipoDato())
                        .permiteNulo(request.permiteNulo())
                        .identificador(request.identificador())
                        .build();

            } else {

                atributo = atributoDiagramaRepository
                        .findById(request.id())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Atributo no encontrado con id: "
                                                + request.id()
                                )
                        );

                if (!atributo.getClase().getId().equals(clase.getId())) {
                    throw new IllegalStateException(
                            "El atributo no pertenece a la clase indicada"
                    );
                }

                atributo.setNombre(request.nombre());
                atributo.setTipoDato(request.tipoDato());
                atributo.setPermiteNulo(request.permiteNulo());
                atributo.setIdentificador(request.identificador());
            }

            atributo = atributoDiagramaRepository.save(atributo);

            atributosConservados.add(atributo.getId());
        }

        for (AtributoDiagrama existente : atributosExistentes) {

            if (!atributosConservados.contains(existente.getId())) {
                atributoDiagramaRepository.delete(existente);
            }
        }
    }

    private void sincronizarRelaciones(
            ModeloDiagrama modelo,
            List<RelacionDiagramaRequest> relacionesRequest,
            Map<String, ClaseDiagrama> clasesPorClave) {

        List<RelacionDiagrama> relacionesExistentes =
                relacionDiagramaRepository.findByModeloId(modelo.getId());

        Set<UUID> relacionesConservadas = new HashSet<>();

        for (RelacionDiagramaRequest request : relacionesRequest) {

            ClaseDiagrama claseOrigen =
                    clasesPorClave.get(request.claseOrigenClave());

            ClaseDiagrama claseDestino =
                    clasesPorClave.get(request.claseDestinoClave());

            ClaseDiagrama claseAsociacion = null;

            if (request.claseAsociacionClave() != null
                    && !request.claseAsociacionClave().isBlank()) {

                claseAsociacion =
                        clasesPorClave.get(request.claseAsociacionClave());

                if (claseAsociacion == null) {
                    throw new IllegalArgumentException(
                            "No existe la clase de asociación con claveCliente: "
                                    + request.claseAsociacionClave()
                    );
                }
            }

            if (claseOrigen == null) {
                throw new IllegalArgumentException(
                        "No existe la clase origen con claveCliente: "
                                + request.claseOrigenClave()
                );
            }

            if (claseDestino == null) {
                throw new IllegalArgumentException(
                        "No existe la clase destino con claveCliente: "
                                + request.claseDestinoClave()
                );
            }

            RelacionDiagrama relacion;

            if (request.id() == null) {

                relacion = RelacionDiagrama.builder()
                        .modelo(modelo)
                        .claseOrigen(claseOrigen)
                        .claseDestino(claseDestino)
                        .tipo(request.tipo())
                        .multiplicidadOrigen(
                                request.multiplicidadOrigen()
                        )
                        .multiplicidadDestino(
                                request.multiplicidadDestino()
                        )
                        .nombre(request.nombre())
                        .claseAsociacion(claseAsociacion)
                        .build();

            } else {

                relacion = relacionDiagramaRepository
                        .findById(request.id())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Relación no encontrada con id: "
                                                + request.id()
                                )
                        );

                if (!relacion.getModelo().getId().equals(modelo.getId())) {
                    throw new IllegalStateException(
                            "La relación no pertenece al modelo actual"
                    );
                }

                relacion.setClaseOrigen(claseOrigen);
                relacion.setClaseDestino(claseDestino);
                relacion.setTipo(request.tipo());
                relacion.setMultiplicidadOrigen(
                        request.multiplicidadOrigen()
                );
                relacion.setMultiplicidadDestino(
                        request.multiplicidadDestino()
                );
                relacion.setNombre(request.nombre());
                relacion.setClaseAsociacion(claseAsociacion);
            }

            relacion = relacionDiagramaRepository.save(relacion);

            relacionesConservadas.add(relacion.getId());
        }

        for (RelacionDiagrama existente : relacionesExistentes) {

            if (!relacionesConservadas.contains(existente.getId())) {
                relacionDiagramaRepository.delete(existente);
            }
        }
    }

    private void eliminarClasesAusentes(
            ModeloDiagrama modelo,
            Set<UUID> clasesConservadas) {

        List<ClaseDiagrama> clasesExistentes =
                claseDiagramaRepository.findByModeloId(modelo.getId());

        for (ClaseDiagrama clase : clasesExistentes) {

            if (!clasesConservadas.contains(clase.getId())) {

                List<AtributoDiagrama> atributos =
                        atributoDiagramaRepository.findByClaseId(
                                clase.getId()
                        );

                atributoDiagramaRepository.deleteAll(atributos);
                claseDiagramaRepository.delete(clase);
            }
        }
    }
}