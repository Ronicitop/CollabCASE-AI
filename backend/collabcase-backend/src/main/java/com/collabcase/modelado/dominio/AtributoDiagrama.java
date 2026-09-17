package com.collabcase.modelado.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "atributos_diagrama",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_atributo_nombre_clase",
                        columnNames = {"clase_id", "nombre"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtributoDiagrama {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(
            name = "clase_id",
            nullable = false
    )
    private ClaseDiagrama clase;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "tipo_dato", nullable = false, length = 50)
    private String tipoDato;

    @Column(nullable = false)
    private Boolean permiteNulo;

    @Column(nullable = false)
    private Boolean identificador;
}
