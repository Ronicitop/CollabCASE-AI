package com.collabcase.modelado.servicio;

import com.collabcase.modelado.dominio.ModeloDiagrama;
import com.collabcase.modelado.repositorio.ModeloDiagramaRepository;
import com.collabcase.proyecto.dominio.Proyecto;
import com.collabcase.proyecto.repositorio.ProyectoRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ModeloDiagramaService {

    private final ModeloDiagramaRepository modeloDiagramaRepository;
    private final ProyectoRepository proyectoRepository;

    public ModeloDiagramaService(
            ModeloDiagramaRepository modeloDiagramaRepository,
            ProyectoRepository proyectoRepository) {

        this.modeloDiagramaRepository = modeloDiagramaRepository;
        this.proyectoRepository = proyectoRepository;
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
}
