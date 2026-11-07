package com.collabcase.ia.servicio;

import java.util.UUID;

public interface ServicioIaPar {
    String procesarConsultaEnSala(UUID sesionId, String remitenteNombre, String consulta);
}