package com.collabcase.colaboracion.repositorio;

import com.collabcase.colaboracion.dominio.MensajeSala;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MensajeSalaRepository extends JpaRepository<MensajeSala, UUID> {
    List<MensajeSala> findBySesionIdOrderByFechaHoraAsc(UUID sesionId);

    @Query(value = """
            SELECT m.* FROM mensaje_sala m
            JOIN sesiones_colaborativas s ON s.id = m.sesion_id
            WHERE s.proyecto_id = :proyectoId
            ORDER BY m.fecha_hora ASC, m.id ASC
            """, nativeQuery = true)
    List<MensajeSala> findHistorialProyecto(@Param("proyectoId") UUID proyectoId);
}

