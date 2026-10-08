package com.talleres360.auditoria.repository;

import com.talleres360.auditoria.model.EventoAuditoria;
import org.springframework.data.jpa.repository.*;

public interface EventoAuditoriaRepository
    extends JpaRepository<EventoAuditoria, String>, JpaSpecificationExecutor<EventoAuditoria> {}
