package com.talleres360.mensajeria.service;

import com.talleres360.mensajeria.model.Topico;
import com.talleres360.mensajeria.repository.KafkaRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaService {
  private final KafkaRepository repository;
  private static final Set<String> CONFIG =
      Set.of(
          "cleanup.policy",
          "retention.ms",
          "retention.bytes",
          "segment.bytes",
          "min.insync.replicas");

  public Object nodos() throws Exception {
    return repository.nodos();
  }

  public Object topicos() throws Exception {
    return repository.topicos();
  }

  public Object configuracion(String nombre) throws Exception {
    validar(nombre);
    return repository.configuracion(nombre);
  }

  public void crear(Topico t) throws Exception {
    validar(t.nombre());
    validarConfig(t.configuracion());
    repository.crear(
        t.nombre(),
        t.particiones(),
        t.replicas(),
        t.configuracion() == null ? Map.of() : t.configuracion());
  }

  public void configurar(String nombre, Map<String, String> valores) throws Exception {
    validar(nombre);
    validarConfig(valores);
    repository.configurar(nombre, valores);
  }

  public void eliminar(String nombre) throws Exception {
    validar(nombre);
    if (!nombre.startsWith("demo."))
      throw new IllegalArgumentException(
          "Los tópicos de negocio están protegidos; solo se eliminan tópicos demo.");
    repository.eliminar(nombre);
  }

  public void inicializar() throws Exception {
    if (repository.nodos().size() != 3)
      throw new IllegalArgumentException("Se requieren tres brokers activos");
    for (String nombre :
        List.of(
            "orders.events",
            "audit.timeline",
            "orders.events.audit.DLT",
            "orders.events.report.DLT")) {
      Map<String, String> config = new HashMap<>();
      config.put("cleanup.policy", nombre.equals("audit.timeline") ? "compact,delete" : "delete");
      config.put("retention.ms", nombre.equals("audit.timeline") ? "1209600000" : "604800000");
      // Límite por partición para el disco de 8 GB del laboratorio; tiempo y tamaño actúan juntos.
      config.put("retention.bytes", "33554432");
      config.put("segment.bytes", "8388608");
      config.put("min.insync.replicas", "2");
      repository.crear(nombre, 3, (short) 3, config);
    }
  }

  private void validar(String n) {
    if (n == null || !n.matches("[a-zA-Z0-9._-]{1,100}"))
      throw new IllegalArgumentException("Nombre inválido");
  }

  private void validarConfig(Map<String, String> c) {
    if (c == null) return;
    if (!CONFIG.containsAll(c.keySet()))
      throw new IllegalArgumentException("Configuración no permitida");
    for (var entrada : c.entrySet()) {
      String valor = entrada.getValue();
      if (valor == null) throw new IllegalArgumentException("Falta un valor de configuración");
      if (entrada.getKey().equals("cleanup.policy")) {
        if (!Set.of("delete", "compact", "compact,delete").contains(valor))
          throw new IllegalArgumentException("Política inválida");
      } else {
        long numero;
        try {
          numero = Long.parseLong(valor);
        } catch (NumberFormatException error) {
          throw new IllegalArgumentException("Se requiere un valor numérico");
        }
        if (entrada.getKey().equals("min.insync.replicas")) {
          if (numero < 2 || numero > 3)
            throw new IllegalArgumentException("Se requieren al menos dos réplicas sincronizadas");
        } else if (numero < 1) throw new IllegalArgumentException("El límite debe ser positivo");
      }
    }
  }
}
