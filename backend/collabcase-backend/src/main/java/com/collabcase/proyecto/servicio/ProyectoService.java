package com.collabcase.proyecto.servicio;

import com.collabcase.proyecto.dominio.Proyecto;
import com.collabcase.proyecto.repositorio.ProyectoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProyectoService {

    private final ProyectoRepository proyectoRepository;

    public ProyectoService(ProyectoRepository proyectoRepository) {
        this.proyectoRepository = proyectoRepository;
    }

    public Proyecto crear(Proyecto proyecto) {
        return proyectoRepository.save(proyecto);
    }

    public List<Proyecto> listarTodos() {
        return proyectoRepository.findAll();
    }

    public Proyecto buscarPorId(UUID id) {
        return proyectoRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Proyecto no encontrado con id: " + id
                        )
                );
    }

    public Proyecto actualizar(UUID id, Proyecto datosProyecto) {

        Proyecto proyecto = buscarPorId(id);

        proyecto.setNombre(datosProyecto.getNombre());
        proyecto.setDescripcion(datosProyecto.getDescripcion());

        return proyectoRepository.save(proyecto);
    }

    public void eliminar(UUID id) {
    Proyecto proyecto = buscarPorId(id);
    proyectoRepository.delete(proyecto);
}
}