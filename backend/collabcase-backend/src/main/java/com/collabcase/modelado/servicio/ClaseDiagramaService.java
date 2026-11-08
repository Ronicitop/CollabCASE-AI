package com.collabcase.modelado.servicio;

import com.collabcase.modelado.dominio.ClaseDiagrama;
import com.collabcase.modelado.dominio.ModeloDiagrama;
import com.collabcase.modelado.repositorio.ClaseDiagramaRepository;
import com.collabcase.modelado.repositorio.ModeloDiagramaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.collabcase.notificacion.NotificacionesService;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ClaseDiagramaService {

    private final NotificacionesService notificaciones;

    private final ClaseDiagramaRepository claseDiagramaRepository;
    private final ModeloDiagramaRepository modeloDiagramaRepository;

    public ClaseDiagramaService(
            NotificacionesService notificaciones,
            ClaseDiagramaRepository claseDiagramaRepository,
            ModeloDiagramaRepository modeloDiagramaRepository) {

        this.notificaciones = notificaciones;
        this.claseDiagramaRepository = claseDiagramaRepository;
        this.modeloDiagramaRepository = modeloDiagramaRepository;
    }

    public ClaseDiagrama crear(
            UUID modeloId,
            ClaseDiagrama datosClase) {

        ModeloDiagrama modelo = modeloDiagramaRepository.findById(modeloId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Modelo de diagrama no encontrado con id: " + modeloId
                        )
                );

        if (claseDiagramaRepository
                .findByModeloIdAndNombre(modeloId, datosClase.getNombre())
                .isPresent()) {

            throw new IllegalStateException(
                    "Ya existe una clase con el nombre: " + datosClase.getNombre()
            );
        }

        ClaseDiagrama clase = ClaseDiagrama.builder()
                .modelo(modelo)
                .nombre(datosClase.getNombre())
                .posicionX(datosClase.getPosicionX())
                .posicionY(datosClase.getPosicionY())
                .build();

        notificaciones.modelo(clase.getModelo().getProyecto().getId());
        return claseDiagramaRepository.save(clase);
    }

    public List<ClaseDiagrama> listarPorModelo(UUID modeloId) {
        return claseDiagramaRepository.findByModeloId(modeloId);
    }

    public ClaseDiagrama buscarPorId(UUID id) {
        return claseDiagramaRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Clase de diagrama no encontrada con id: " + id
                        )
                );
    }

    public ClaseDiagrama actualizar(
            UUID id,
            ClaseDiagrama datosClase) {

        ClaseDiagrama clase = buscarPorId(id);

        if (!clase.getNombre().equals(datosClase.getNombre())
                && claseDiagramaRepository
                .findByModeloIdAndNombre(
                        clase.getModelo().getId(),
                        datosClase.getNombre()
                )
                .isPresent()) {

            throw new IllegalStateException(
                    "Ya existe una clase con el nombre: " + datosClase.getNombre()
            );
        }

        boolean cambioContenido = !java.util.Objects.equals(clase.getNombre(), datosClase.getNombre());
        clase.setNombre(datosClase.getNombre());
        clase.setPosicionX(datosClase.getPosicionX());
        clase.setPosicionY(datosClase.getPosicionY());

        if (cambioContenido) notificaciones.modelo(clase.getModelo().getProyecto().getId());
        return claseDiagramaRepository.save(clase);
    }

    public void eliminar(UUID id) {
        ClaseDiagrama clase = buscarPorId(id);
        notificaciones.modelo(clase.getModelo().getProyecto().getId());
        claseDiagramaRepository.delete(clase);
    }
}
