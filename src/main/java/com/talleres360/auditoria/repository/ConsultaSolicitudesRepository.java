package com.talleres360.auditoria.repository;

import com.talleres360.auditoria.dto.SolicitudVista;
import com.talleres360.auditoria.model.EventoAuditoria;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** Pagina solicitudes en la base, sin cargar ni fragmentar su historial completo. */
@Repository
@RequiredArgsConstructor
public class ConsultaSolicitudesRepository {
  private final EntityManager em;

  public Page<SolicitudVista> consultar(Specification<EventoAuditoria> filtro, Pageable pagina) {
    var cb = em.getCriteriaBuilder();
    var consulta = cb.createQuery(SolicitudVista.class);
    var evento = consulta.from(EventoAuditoria.class);
    var ultima = cb.greatest(evento.<Instant>get("occurredAt"));
    consulta.select(cb.construct(SolicitudVista.class, evento.get("orderId"), cb.count(evento), ultima))
        .where(filtro.toPredicate(evento, consulta, cb))
        .groupBy(evento.get("orderId"))
        .orderBy(cb.desc(ultima), cb.desc(evento.get("orderId")));
    var contenido = em.createQuery(consulta).setFirstResult(Math.toIntExact(pagina.getOffset()))
        .setMaxResults(pagina.getPageSize()).getResultList();
    var contar = cb.createQuery(Long.class);
    var raiz = contar.from(EventoAuditoria.class);
    contar.select(cb.countDistinct(raiz.get("orderId"))).where(filtro.toPredicate(raiz, contar, cb));
    return new PageImpl<>(contenido, pagina, em.createQuery(contar).getSingleResult());
  }
}
