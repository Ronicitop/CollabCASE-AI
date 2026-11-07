package com.collabcase.colaboracion.repositorio;

import com.collabcase.colaboracion.dominio.AuditoriaOperacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditoriaOperacionRepository extends JpaRepository<AuditoriaOperacion, UUID> {
    List<AuditoriaOperacion> findBySesionIdOrderByFechaHoraAsc(UUID sesionId);
    List<AuditoriaOperacion> findTop15BySesionIdOrderByFechaHoraDesc(UUID sesionId);
}
