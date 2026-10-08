package com.talleres360.auditoria;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.auditoria.dto.EventoEntrada;
import com.talleres360.auditoria.repository.EventoAuditoriaRepository;
import com.talleres360.auditoria.service.AuditoriaService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "INTERNAL_API_KEY=test-key")
@AutoConfigureMockMvc
class AuditoriaTest {
  @Autowired AuditoriaService service;
  @Autowired EventoAuditoriaRepository repository;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void limpiar() {
    repository.deleteAll();
  }

  private EventoEntrada evento(Instant fecha) {
    return new EventoEntrada(
        UUID.randomUUID().toString(),
        1L,
        "ACEPTADA",
        "operador@ejemplo.invalid",
        "Revisión solicitada",
        fecha,
        "ACEPTADA",
        BigDecimal.TEN,
        "orders",
        "traza-1");
  }

  @Test
  void idempotenteYFiltrablePorActorTipoOrdenYFechas() {
    var fecha = Instant.parse("2026-10-08T10:00:00Z");
    var e = evento(fecha);
    service.registrar(e);
    service.registrar(e);
    assertEquals(1, repository.count());
    assertEquals(
        1,
        service
            .consultar(
                1L, "OPERADOR@ejemplo.invalid", "ACEPTADA", fecha, fecha.plusSeconds(1), 0, 20)
            .getTotalElements());
    assertEquals(
        0,
        service.consultar(1L, null, null, fecha.minusSeconds(1), fecha, 0, 20).getTotalElements());
  }

  @Test
  void rechazaReutilizacionDeEventoYRangosInvalidos() {
    var e = evento(Instant.now());
    service.registrar(e);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.registrar(
                new EventoEntrada(
                    e.eventId(),
                    2L,
                    e.type(),
                    e.actor(),
                    e.reason(),
                    e.occurredAt(),
                    e.status(),
                    e.total(),
                    e.source(),
                    e.correlationId())));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.consultar(null, null, null, Instant.MAX, Instant.MIN, 0, 20));
  }

  @Test
  void soloConsultaExternaEIngestaInternaProtegida() throws Exception {
    mvc.perform(get("/api/audit/events")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/audit/events")
                .header("X-Internal-Key", "test-key")
                .header("X-Actor-Role", "Admin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
    mvc.perform(
            get("/api/reports/audit")
                .header("X-Internal-Key", "test-key")
                .header("X-Actor-Role", "Admin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
    mvc.perform(
            get("/api/audit/events")
                .header("X-Internal-Key", "test-key")
                .header("X-Actor-Role", "Operador"))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/audit/events/1").header("X-Internal-Key", "test-key"))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/audit/events")
                .param("page", "-1")
                .header("X-Internal-Key", "test-key")
                .header("X-Actor-Role", "Admin"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void precisionTemporalYOrigenNoPermitenAlterarUnEvento() {
    var e = evento(Instant.parse("2026-10-08T11:00:00.123456789Z"));
    service.registrar(e);
    service.registrar(e);
    assertEquals(1, repository.count());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.registrar(
                new EventoEntrada(
                    e.eventId(),
                    e.orderId(),
                    e.type(),
                    e.actor(),
                    e.reason(),
                    e.occurredAt(),
                    e.status(),
                    e.total(),
                    "otro-servicio",
                    e.correlationId())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.registrar(
                new EventoEntrada(
                    e.eventId(),
                    e.orderId(),
                    e.type(),
                    e.actor(),
                    e.reason(),
                    e.occurredAt(),
                    e.status(),
                    e.total(),
                    e.source(),
                    "otra-traza")));
  }

  @Test
  void ingestaHttpYConsultaDevuelvenLaLineaDeTiempo() throws Exception {
    var e = evento(Instant.parse("2026-10-08T12:00:00Z"));
    mvc.perform(
            post("/internal/events")
                .contentType("application/json")
                .content(json.writeValueAsString(e)))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/internal/events")
                .header("X-Internal-Key", "test-key")
                .contentType("application/json")
                .content(json.writeValueAsString(e)))
        .andExpect(status().isNoContent());
    mvc.perform(
            get("/api/audit/events")
                .header("X-Internal-Key", "test-key")
                .header("X-Actor-Role", "Admin")
                .param("orderId", "1")
                .param("type", "ACEPTADA")
                .param("actor", e.actor())
                .param("from", "2026-10-08T12:00:00Z")
                .param("to", "2026-10-08T13:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].eventId").value(e.eventId()));
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").exists());
  }

  @Test
  void validaTipoEstadoMontoYCamposObligatorios() {
    var e = evento(Instant.now());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.registrar(
                new EventoEntrada(
                    e.eventId(),
                    e.orderId(),
                    "INVENTADO",
                    e.actor(),
                    e.reason(),
                    e.occurredAt(),
                    e.status(),
                    e.total(),
                    e.source(),
                    e.correlationId())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.registrar(
                new EventoEntrada(
                    e.eventId(),
                    e.orderId(),
                    e.type(),
                    e.actor(),
                    e.reason(),
                    e.occurredAt(),
                    "INVENTADO",
                    e.total(),
                    e.source(),
                    e.correlationId())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.registrar(
                new EventoEntrada(
                    e.eventId(),
                    e.orderId(),
                    e.type(),
                    e.actor(),
                    e.reason(),
                    e.occurredAt(),
                    e.status(),
                    new BigDecimal("-1"),
                    e.source(),
                    e.correlationId())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.registrar(
                new EventoEntrada(
                    e.eventId(),
                    e.orderId(),
                    e.type(),
                    e.actor(),
                    e.reason(),
                    e.occurredAt(),
                    e.status(),
                    new BigDecimal("1.123"),
                    e.source(),
                    e.correlationId())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.registrar(
                new EventoEntrada(
                    e.eventId(),
                    e.orderId(),
                    e.type(),
                    "",
                    e.reason(),
                    e.occurredAt(),
                    e.status(),
                    e.total(),
                    e.source(),
                    e.correlationId())));
  }
}
