package com.talleres360.mensajeria.repository;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class KafkaRepository implements AutoCloseable {
  private final Admin cliente;

  public KafkaRepository(@Value("${spring.kafka.bootstrap-servers}") String servidores) {
    cliente =
        Admin.create(
            Map.of(
                "bootstrap.servers",
                servidores,
                "request.timeout.ms",
                5000,
                "default.api.timeout.ms",
                15000));
  }

  public Collection<Map<String, Object>> nodos() throws Exception {
    return cliente.describeCluster().nodes().get(15, TimeUnit.SECONDS).stream()
        .map(n -> Map.<String, Object>of("id", n.id(), "host", n.host(), "puerto", n.port()))
        .toList();
  }

  public Object topicos() throws Exception {
    var nombres = cliente.listTopics().names().get(15, TimeUnit.SECONDS);
    return cliente
        .describeTopics(nombres)
        .allTopicNames()
        .get(15, TimeUnit.SECONDS)
        .values()
        .stream()
        .map(
            t ->
                Map.of(
                    "nombre",
                    t.name(),
                    "particiones",
                    t.partitions().stream()
                        .map(
                            p ->
                                Map.of(
                                    "id",
                                    p.partition(),
                                    "replicas",
                                    p.replicas().size(),
                                    "sincronizadas",
                                    p.isr().size()))
                        .toList()))
        .toList();
  }

  public void crear(String nombre, int particiones, short replicas, Map<String, String> config)
      throws Exception {
    try {
      cliente
          .createTopics(List.of(new NewTopic(nombre, particiones, replicas).configs(config)))
          .all()
          .get(15, TimeUnit.SECONDS);
    } catch (java.util.concurrent.ExecutionException e) {
      if (!(e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException)) throw e;
    }
  }

  public Object configuracion(String nombre) throws Exception {
    var recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
    return cliente
        .describeConfigs(List.of(recurso))
        .all()
        .get(15, TimeUnit.SECONDS)
        .get(recurso)
        .entries()
        .stream()
        .filter(e -> !e.isSensitive())
        .map(e -> Map.of("nombre", e.name(), "valor", String.valueOf(e.value())))
        .toList();
  }

  public void configurar(String nombre, Map<String, String> valores) throws Exception {
    var recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
    var cambios =
        valores.entrySet().stream()
            .map(
                e ->
                    new AlterConfigOp(
                        new ConfigEntry(e.getKey(), e.getValue()), AlterConfigOp.OpType.SET))
            .toList();
    cliente.incrementalAlterConfigs(Map.of(recurso, cambios)).all().get(15, TimeUnit.SECONDS);
  }

  public void eliminar(String nombre) throws Exception {
    cliente.deleteTopics(List.of(nombre)).all().get(15, TimeUnit.SECONDS);
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    cliente.close(java.time.Duration.ofSeconds(3));
  }
}
