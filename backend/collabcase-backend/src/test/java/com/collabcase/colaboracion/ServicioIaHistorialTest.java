package com.collabcase.colaboracion;

import com.collabcase.colaboracion.dominio.AuditoriaOperacion;
import com.collabcase.colaboracion.dto.MensajeSalaDTO;
import com.collabcase.colaboracion.repositorio.AuditoriaOperacionRepository;
import com.collabcase.colaboracion.servicio.ChatProyectoService;
import com.collabcase.ia.servicio.ServicioIaParImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ServicioIaHistorialTest {
    @Test void promptIncluyeTodasLasSesionesPeroAuditoriaDeLaSesionActual() {
        var chat = mock(ChatProyectoService.class);
        var auditoria = mock(AuditoriaOperacionRepository.class);
        UUID actual = UUID.randomUUID(), anterior = UUID.randomUUID();
        when(chat.historialSesion(actual)).thenReturn(List.of(
                new MensajeSalaDTO(UUID.randomUUID(), anterior, "Ana", "USUARIO", "Mensaje anterior del proyecto", null),
                new MensajeSalaDTO(UUID.randomUUID(), actual, "Luis", "USUARIO", "Mensaje actual del proyecto", null)));
        when(auditoria.findTop15BySesionIdOrderByFechaHoraDesc(actual)).thenReturn(List.of(
                new AuditoriaOperacion(actual, null, "Luis", "CAMBIO", "Auditoria de esta sesion")));
        var ia = new ServicioIaParImpl(chat, auditoria);
        ReflectionTestUtils.setField(ia, "motorActivo", "LOCAL");
        ReflectionTestUtils.setField(ia, "ollamaUrl", "http://ia.test/api/generate");
        ReflectionTestUtils.setField(ia, "ollamaModel", "simulado");
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(ia, "restTemplate")).build();
        server.expect(requestTo("http://ia.test/api/generate")).andExpect(request -> {
            String body = new String(((MockClientHttpRequest) request).getBodyAsBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("Mensaje anterior del proyecto"));
            assertTrue(body.contains("Mensaje actual del proyecto"));
            assertTrue(body.contains("Auditoria de esta sesion"));
        }).andRespond(withSuccess("{\"response\":\"Respuesta simulada\"}", MediaType.APPLICATION_JSON));
        assertEquals("Respuesta simulada", ia.procesarConsultaEnSala(actual, "Luis", "Explica"));
        server.verify();
        verify(chat).historialSesion(actual);
        verify(auditoria).findTop15BySesionIdOrderByFechaHoraDesc(actual);
        verifyNoMoreInteractions(chat, auditoria);
    }
}
