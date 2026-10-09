package com.talleres360.mensajeria.model;

import jakarta.validation.constraints.*;
import java.util.Map;

public record Topico(
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{1,100}") String nombre,
    @Min(1) @Max(12) int particiones,
    @Min(3) @Max(3) short replicas,
    Map<String, String> configuracion) {}
