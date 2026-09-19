package com.collabcase.ia.controlador;

import com.collabcase.ia.dto.ChatIaRequest;
import com.collabcase.ia.dto.ChatIaResponse;
import com.collabcase.ia.servicio.ChatIaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return chatIaService.procesar(
                codigo,
                request
        );
    }
}
