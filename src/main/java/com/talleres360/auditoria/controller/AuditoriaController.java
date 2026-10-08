package com.talleres360.auditoria.controller;

import com.talleres360.auditoria.dto.EventoEntrada;
import com.talleres360.auditoria.dto.EventoVista;
import com.talleres360.auditoria.dto.Pagina;
import com.talleres360.auditoria.service.AuditoriaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class AuditoriaController {
  private final AuditoriaService service;

  @PostMapping("/internal/events")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void registrar(@Valid @RequestBody EventoEntrada input) {
    service.registrar(input);
  }

  @GetMapping({"/api/audit", "/api/audit/events"})
  public Pagina<EventoVista> consultar(
      @RequestParam(required = false) @Positive Long orderId,
      @RequestParam(required = false) @Size(max = 160) String actor,
      @RequestParam(required = false) @Size(max = 60) String type,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestHeader("X-Actor-Role") String role) {
    exigirAdministrador(role);
    return Pagina.desde(
        service.consultar(orderId, actor, type, from, to, page, size).map(EventoVista::desde));
  }

  // Compatibilidad con el dashboard existente: devuelve lista, no Page.
  @GetMapping("/api/reports/audit")
  public List<EventoVista> anterior(
      @RequestParam(required = false) @Positive Long orderId,
      @RequestHeader("X-Actor-Role") String role) {
    exigirAdministrador(role);
    return service
        .consultar(orderId, null, null, null, null, 0, 100)
        .map(EventoVista::desde)
        .getContent();
  }

  private void exigirAdministrador(String role) {
    if (!role.equals("Admin")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }
}
