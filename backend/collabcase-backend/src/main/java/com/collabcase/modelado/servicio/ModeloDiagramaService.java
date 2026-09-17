package com.collabcase.modelado.servicio;

import com.collabcase.modelado.dominio.AtributoDiagrama;
import com.collabcase.modelado.dominio.ClaseDiagrama;
import com.collabcase.modelado.dominio.ModeloDiagrama;
import com.collabcase.modelado.dominio.RelacionDiagrama;
import com.collabcase.modelado.dto.AtributoDiagramaResponse;
import com.collabcase.modelado.dto.ClaseDiagramaResponse;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import com.collabcase.modelado.dto.RelacionDiagramaResponse;
import com.collabcase.modelado.repositorio.AtributoDiagramaRepository;
import com.collabcase.modelado.repositorio.ClaseDiagramaRepository;
import com.collabcase.modelado.repositorio.ModeloDiagramaRepository;
import com.collabcase.modelado.repositorio.RelacionDiagramaRepository;
import com.collabcase.proyecto.dominio.Proyecto;
import com.collabcase.proyecto.repositorio.ProyectoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
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
                                        relacion.getNombre()
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
}