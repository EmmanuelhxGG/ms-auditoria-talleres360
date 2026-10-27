package com.talleres360.auditoria.service;

import com.talleres360.auditoria.dto.EventoEntrada;
import com.talleres360.auditoria.dto.SolicitudVista;
import com.talleres360.auditoria.model.EventoAuditoria;
import com.talleres360.auditoria.repository.EventoAuditoriaRepository;
import com.talleres360.auditoria.repository.ConsultaSolicitudesRepository;
import jakarta.validation.Validator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditoriaService {
  private static final Set<String> TIPOS =
      Set.of(
          "CREADA",
          "ACTUALIZADA",
          "ACEPTADA",
          "EN_REPARACION",
          "LISTA_PARA_ENTREGA",
          "ENTREGADA",
          "CANCELADA",
          "ELIMINADA",
          "INFORME_ACTUALIZADO");
  private static final Set<String> ESTADOS =
      Set.of(
          "RECIBIDA", "ACEPTADA", "EN_REPARACION", "LISTA_PARA_ENTREGA", "ENTREGADA", "CANCELADA");
  private final EventoAuditoriaRepository repository;
  private final Validator validator;
  private final ConsultaSolicitudesRepository consultasSolicitudes;

  @Transactional
  public void registrar(EventoEntrada input) {
    if (!validator.validate(input).isEmpty()
        || !TIPOS.contains(input.type())
        || !ESTADOS.contains(input.status())) throw new IllegalArgumentException("Evento inválido");
    var anterior = repository.findById(input.eventId());
    if (anterior.isPresent()) {
      var e = anterior.get();
      if (!e.getOrderId().equals(input.orderId())
          || !e.getType().equals(input.type())
          || !e.getOccurredAt()
              .truncatedTo(ChronoUnit.MICROS)
              .equals(input.occurredAt().truncatedTo(ChronoUnit.MICROS))
          || !e.getActor().equals(input.actor())
          || !Objects.equals(e.getReason(), input.reason())
          || !e.getStatus().equals(input.status())
          || e.getTotal().compareTo(input.total()) != 0
          || !Objects.equals(
              e.getSource(), input.source() == null ? "ms-talleres360-orders" : input.source())
          || !Objects.equals(e.getCorrelationId(), input.correlationId()))
        throw new IllegalArgumentException("El eventId ya identifica otro evento");
      return;
    }
    var evento = new EventoAuditoria();
    evento.setEventId(input.eventId());
    evento.setOrderId(input.orderId());
    evento.setType(input.type());
    evento.setActor(input.actor());
    evento.setReason(input.reason());
    evento.setOccurredAt(input.occurredAt().truncatedTo(ChronoUnit.MICROS));
    evento.setStatus(input.status());
    evento.setTotal(input.total());
    evento.setSource(input.source() == null ? "ms-talleres360-orders" : input.source());
    evento.setCorrelationId(input.correlationId());
    repository.saveAndFlush(evento);
  }

  @Transactional(readOnly = true)
  public Page<EventoAuditoria> consultar(
      Long ordenId,
      String actor,
      String tipo,
      Instant desde,
      Instant hasta,
      int pagina,
      int tamano) {
    return repository.findAll(filtro(ordenId, actor, tipo, desde, hasta),
        PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by("eventId"))));
  }

  @Transactional(readOnly = true)
  public Page<SolicitudVista> solicitudes(
      Long ordenId, String actor, String tipo, Instant desde, Instant hasta, int pagina, int tamano) {
    return consultasSolicitudes.consultar(filtro(ordenId, actor, tipo, desde, hasta), PageRequest.of(pagina, tamano));
  }

  private Specification<EventoAuditoria> filtro(
      Long ordenId, String actor, String tipo, Instant desde, Instant hasta) {
    if (desde != null && hasta != null && !desde.isBefore(hasta))
      throw new IllegalArgumentException("Rango inválido");
    if (tipo != null && !TIPOS.contains(tipo)) throw new IllegalArgumentException("Tipo inválido");
    return (root, query, cb) -> {
          var filtros = new ArrayList<jakarta.persistence.criteria.Predicate>();
          if (ordenId != null) filtros.add(cb.equal(root.get("orderId"), ordenId));
          if (actor != null)
            filtros.add(
                cb.equal(cb.lower(root.get("actor")), actor.trim().toLowerCase(Locale.ROOT)));
          if (tipo != null) filtros.add(cb.equal(root.get("type"), tipo));
          if (desde != null) filtros.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), desde));
          if (hasta != null) filtros.add(cb.lessThan(root.get("occurredAt"), hasta));
          return cb.and(filtros.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
  }
}
