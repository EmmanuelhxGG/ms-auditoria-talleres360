package com.talleres360.auditoria.dto;

import java.time.Instant;

public record SolicitudVista(Long orderId, long eventos, Instant ultimaActividad) {}
