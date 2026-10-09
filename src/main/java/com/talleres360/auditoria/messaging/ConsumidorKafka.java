package com.talleres360.auditoria.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.auditoria.dto.EventoEntrada;
import com.talleres360.auditoria.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class ConsumidorKafka {
  private final AuditoriaService service;
  private final ObjectMapper json;
  private final org.springframework.kafka.core.KafkaTemplate<String, String> kafka;

  @KafkaListener(
      topics = "${app.kafka.topic:orders.events}",
      groupId = "${app.kafka.group-id:talleres360-auditoria}")
  public void consumir(String mensaje) throws Exception {
    var evento = json.readValue(mensaje, EventoEntrada.class);
    service.registrar(evento);
    // El offset solo avanza después de persistir y confirmar el timeline en Kafka.
    kafka
        .send("audit.timeline", evento.eventId(), mensaje)
        .get(15, java.util.concurrent.TimeUnit.SECONDS);
  }
}
