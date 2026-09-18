package com.collabcase.colaboracion.dominio;

import com.collabcase.proyecto.dominio.Proyecto;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "sesiones_colaborativas",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_sesion_colaborativa_codigo",
            columnNames = "codigo"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SesionColaborativa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 8)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @Column(nullable = false)
    private boolean activa;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @PrePersist
    public void prePersist() {
        if (creadaEn == null) {
            creadaEn = LocalDateTime.now();
        }
    }
}
