package com.talleres360.auditoria.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

@Entity
@Immutable
@Table(
    name = "eventos_auditoria",
    indexes = {
      @Index(name = "idx_audit_orden", columnList = "solicitud_id"),
      @Index(name = "idx_audit_fecha", columnList = "ocurrido_en"),
      @Index(name = "idx_audit_actor", columnList = "usuario_accion")
    })
@Getter
@Setter
@NoArgsConstructor
public class EventoAuditoria implements Persistable<String> {
  @Transient private boolean nuevo = true;

  @Override
  @JsonIgnore
  public String getId() {
    return eventId;
  }

  @Override
  @JsonIgnore
  public boolean isNew() {
    return nuevo;
  }

  @PostLoad
  @PostPersist
  private void marcarExistente() {
    nuevo = false;
  }

  @Id
  @Column(name = "evento_id", length = 36)
  private String eventId;

  @Column(name = "solicitud_id", nullable = false)
  private Long orderId;

  @Column(name = "tipo", nullable = false, length = 60)
  private String type;

  @Column(name = "usuario_accion", nullable = false, length = 160)
  private String actor;

  @Column(name = "motivo", length = 500)
  private String reason;

  @Column(name = "ocurrido_en", nullable = false)
  private Instant occurredAt;

  @Column(name = "estado", nullable = false, length = 30)
  private String status;

  @Column(name = "total", nullable = false, precision = 12, scale = 2)
  private BigDecimal total;

  @Column(name = "origen", length = 80)
  private String source;

  @Column(name = "correlacion_id", length = 80)
  private String correlationId;
}
