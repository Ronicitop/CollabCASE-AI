package com.collabcase.modelado.servicio;

import com.collabcase.modelado.dominio.ClaseDiagrama;
import com.collabcase.modelado.dominio.ModeloDiagrama;
import com.collabcase.modelado.dominio.RelacionDiagrama;
import com.collabcase.modelado.repositorio.ClaseDiagramaRepository;
import com.collabcase.modelado.repositorio.ModeloDiagramaRepository;
import com.collabcase.modelado.repositorio.RelacionDiagramaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RelacionDiagramaService {

    private final RelacionDiagramaRepository relacionDiagramaRepository;
    private final ModeloDiagramaRepository modeloDiagramaRepository;
    private final ClaseDiagramaRepository claseDiagramaRepository;

    public RelacionDiagramaService(
            RelacionDiagramaRepository relacionDiagramaRepository,
            ModeloDiagramaRepository modeloDiagramaRepository,
            ClaseDiagramaRepository claseDiagramaRepository) {

        this.relacionDiagramaRepository = relacionDiagramaRepository;
        this.modeloDiagramaRepository = modeloDiagramaRepository;
        this.claseDiagramaRepository = claseDiagramaRepository;
    }

    public RelacionDiagrama crear(
            UUID modeloId,
            UUID claseOrigenId,
            UUID claseDestinoId,
            RelacionDiagrama datosRelacion) {

        ModeloDiagrama modelo = modeloDiagramaRepository.findById(modeloId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Modelo de diagrama no encontrado con id: " + modeloId
                        )
                );

        ClaseDiagrama claseOrigen = claseDiagramaRepository.findById(claseOrigenId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Clase origen no encontrada con id: " + claseOrigenId
                        )
                );

        ClaseDiagrama claseDestino = claseDiagramaRepository.findById(claseDestinoId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Clase destino no encontrada con id: " + claseDestinoId
                        )
                );

        if (!claseOrigen.getModelo().getId().equals(modeloId)
                || !claseDestino.getModelo().getId().equals(modeloId)) {

            throw new IllegalStateException(
                    "Las clases de la relación deben pertenecer al mismo modelo"
            );
        }

        RelacionDiagrama relacion = RelacionDiagrama.builder()
                .modelo(modelo)
                .claseOrigen(claseOrigen)
                .claseDestino(claseDestino)
                .tipo(datosRelacion.getTipo())
                .multiplicidadOrigen(datosRelacion.getMultiplicidadOrigen())
                .multiplicidadDestino(datosRelacion.getMultiplicidadDestino())
                .nombre(datosRelacion.getNombre())
                .build();

        return relacionDiagramaRepository.save(relacion);
    }

    public List<RelacionDiagrama> listarPorModelo(UUID modeloId) {
        return relacionDiagramaRepository.findByModeloId(modeloId);
    }

    public RelacionDiagrama buscarPorId(UUID id) {
        return relacionDiagramaRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Relación de diagrama no encontrada con id: " + id
                        )
                );
    }

    public RelacionDiagrama actualizar(
            UUID id,
            RelacionDiagrama datosRelacion) {

        RelacionDiagrama relacion = buscarPorId(id);

        relacion.setTipo(datosRelacion.getTipo());
        relacion.setMultiplicidadOrigen(
                datosRelacion.getMultiplicidadOrigen()
        );
        relacion.setMultiplicidadDestino(
                datosRelacion.getMultiplicidadDestino()
        );
        relacion.setNombre(datosRelacion.getNombre());

        return relacionDiagramaRepository.save(relacion);
    }

    public void eliminar(UUID id) {
        RelacionDiagrama relacion = buscarPorId(id);
        relacionDiagramaRepository.delete(relacion);
    }
}
