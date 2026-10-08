package com.talleres360.auditoria.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record EventoEntrada(
    @NotBlank @Size(max = 36) String eventId,
    @NotNull @Positive Long orderId,
    @NotBlank @Size(max = 60) String type,
    @NotBlank @Size(max = 160) String actor,
    @Size(max = 500) String reason,
    @NotNull Instant occurredAt,
    @NotBlank @Size(max = 30) String status,
    @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal total,
    @Size(max = 80) String source,
    @Size(max = 80) String correlationId) {}
