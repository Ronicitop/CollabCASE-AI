package com.collabcase.proyecto.repositorio;

import com.collabcase.proyecto.dominio.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProyectoRepository extends JpaRepository<Proyecto, UUID> {
}
