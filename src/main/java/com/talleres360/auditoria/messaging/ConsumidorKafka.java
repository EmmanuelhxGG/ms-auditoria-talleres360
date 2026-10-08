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

  @KafkaListener(
      topics = "${app.kafka.topic:orders.events}",
      groupId = "${app.kafka.group-id:talleres360-auditoria}")
  public void consumir(String mensaje) throws java.io.IOException {
    service.registrar(json.readValue(mensaje, EventoEntrada.class));
  }
}
