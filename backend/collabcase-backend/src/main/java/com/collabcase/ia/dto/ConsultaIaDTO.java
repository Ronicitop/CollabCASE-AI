package com.collabcase.ia.dto;

import java.util.UUID;

public class ConsultaIaDTO {
    private UUID sesionId;
    private String remitenteNombre;
    private String consulta;

    public ConsultaIaDTO() {}

    public ConsultaIaDTO(UUID sesionId, String remitenteNombre, String consulta) {
        this.sesionId = sesionId;
        this.remitenteNombre = remitenteNombre;
        this.consulta = consulta;
    }

    public UUID getSesionId() { return sesionId; }
    public void setSesionId(UUID sesionId) { this.sesionId = sesionId; }
    public String getRemitenteNombre() { return remitenteNombre; }
    public void setRemitenteNombre(String remitenteNombre) { this.remitenteNombre = remitenteNombre; }
    public String getConsulta() { return consulta; }
    public void setConsulta(String consulta) { this.consulta = consulta; }
}