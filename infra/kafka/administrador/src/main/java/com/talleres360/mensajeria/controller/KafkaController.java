package com.talleres360.mensajeria.controller;

import com.talleres360.mensajeria.model.Topico;
import com.talleres360.mensajeria.service.KafkaService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messaging/kafka")
@RequiredArgsConstructor
public class KafkaController {
  private final KafkaService service;

  @GetMapping("/nodes")
  public Object nodos() throws Exception {
    return service.nodos();
  }

  @GetMapping("/topics")
  public Object topicos() throws Exception {
    return service.topicos();
  }

  @GetMapping("/topics/{nombre}/config")
  public Object configuracion(@PathVariable String nombre) throws Exception {
    return service.configuracion(nombre);
  }

  @PostMapping("/topics")
  public void crear(@Valid @RequestBody Topico t) throws Exception {
    service.crear(t);
  }

  @PutMapping("/topics/{nombre}/config")
  public void configurar(@PathVariable String nombre, @RequestBody Map<String, String> valores)
      throws Exception {
    service.configurar(nombre, valores);
  }

  @DeleteMapping("/topics/{nombre}")
  public void eliminar(@PathVariable String nombre) throws Exception {
    service.eliminar(nombre);
  }

  @PostMapping("/initialize")
  public void inicializar() throws Exception {
    service.inicializar();
  }
}
