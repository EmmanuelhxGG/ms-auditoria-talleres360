package com.talleres360.auditoria;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.auditoria.dto.EventoEntrada;
import com.talleres360.auditoria.messaging.ConsumidorKafka;
import com.talleres360.auditoria.service.AuditoriaService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class ConsumidorKafkaTest {
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private final AuditoriaService service = mock(AuditoriaService.class);
  private final ConsumidorKafka consumidor = new ConsumidorKafka(service, json);

  @Test
  void interpretaElContratoDeEventosDeOrdenes() throws Exception {
    var e =
        new EventoEntrada(
            UUID.randomUUID().toString(),
            1L,
            "CREADA",
            "admin@ejemplo.invalid",
            null,
            Instant.now(),
            "RECIBIDA",
            BigDecimal.ZERO,
            null,
            null);
    consumidor.consumir(json.writeValueAsString(e));
    verify(service).registrar(e);
  }

  @Test
  void erroresNoSeOcultanParaQueKafkaApliqueReintentosYRecuperacion() {
    assertThrows(java.io.IOException.class, () -> consumidor.consumir("{"));
    doThrow(new DataAccessResourceFailureException("Prueba")).when(service).registrar(any());
    assertThrows(DataAccessResourceFailureException.class, () -> consumidor.consumir("{}"));
  }
}
