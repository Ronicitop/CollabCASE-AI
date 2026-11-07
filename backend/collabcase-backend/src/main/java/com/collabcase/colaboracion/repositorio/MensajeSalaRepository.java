package com.collabcase.colaboracion.repositorio;

import com.collabcase.colaboracion.dominio.MensajeSala;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MensajeSalaRepository extends JpaRepository<MensajeSala, UUID> {
    List<MensajeSala> findBySesionIdOrderByFechaHoraAsc(UUID sesionId);
}

