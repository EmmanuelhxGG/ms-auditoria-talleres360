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
      @Index(name = "idx_audit_orden", columnList = "orderId"),
      @Index(name = "idx_audit_fecha", columnList = "occurredAt"),
      @Index(name = "idx_audit_actor", columnList = "actor")
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
  @Column(length = 36)
  private String eventId;

  @Column(nullable = false)
  private Long orderId;

  @Column(nullable = false, length = 60)
  private String type;

  @Column(nullable = false, length = 160)
  private String actor;

  @Column(length = 500)
  private String reason;

  @Column(nullable = false)
  private Instant occurredAt;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal total;

  @Column(length = 80)
  private String source;

  @Column(length = 80)
  private String correlationId;
}
