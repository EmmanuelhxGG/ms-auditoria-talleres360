package com.talleres360.auditoria.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.auditoria.dto.EventoEntrada;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "KAFKA_ENABLED", havingValue = "true")
public class ConsumidorEventosKafka {
  private final ObjectMapper json;
  private final AuditoriaService auditoria;

  @KafkaListener(topics = "orders.events", groupId = "talleres360-auditoria")
  public void consumir(String mensaje) throws java.io.IOException {
    var contenido = json.readTree(mensaje);
    if (contenido == null || !contenido.path("evento").isObject()
        || !contenido.path("stockConfirmado").isBoolean()) {
      throw new IllegalArgumentException("El mensaje no contiene un evento de orden válido");
    }
    auditoria.registrar(json.treeToValue(contenido.get("evento"), EventoEntrada.class));
  }
}
