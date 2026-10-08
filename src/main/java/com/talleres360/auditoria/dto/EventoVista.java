package com.talleres360.auditoria.dto;

import com.talleres360.auditoria.model.EventoAuditoria;
import java.math.BigDecimal;
import java.time.Instant;

public record EventoVista(
    String eventId,
    Long orderId,
    String type,
    String actor,
    String reason,
    Instant occurredAt,
    String status,
    BigDecimal total,
    String source,
    String correlationId) {
  public static EventoVista desde(EventoAuditoria e) {
    return new EventoVista(
        e.getEventId(),
        e.getOrderId(),
        e.getType(),
        e.getActor(),
        e.getReason(),
        e.getOccurredAt(),
        e.getStatus(),
        e.getTotal(),
        e.getSource(),
        e.getCorrelationId());
  }
}
