package com.collabcase.ia.controlador;

import com.collabcase.ia.dto.ChatIaRequest;
import com.collabcase.ia.dto.ChatIaResponse;
import com.collabcase.ia.servicio.ChatIaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class ChatIaController {

    private final ChatIaService chatIaService;

    @PostMapping("/sesiones/{codigo}/chat")
    public ChatIaResponse chat(
            @PathVariable String codigo,
            @Valid
            @RequestBody ChatIaRequest request
    ) {
        return chatIaService.procesar(codigo, request);
    }

    @PostMapping(
            value = "/sesiones/{codigo}/imagen",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ChatIaResponse editarDesdeImagen(
            @PathVariable String codigo,
            @RequestPart("archivo") MultipartFile archivo,
            @RequestPart("clienteId") String clienteId,
            @RequestPart(value = "mensaje", required = false) String mensaje
    ) {
        return chatIaService.procesarImagen(
                codigo,
                clienteId,
                mensaje,
                archivo
        );
    }
}
