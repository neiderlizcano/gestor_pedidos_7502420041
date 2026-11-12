package com.neider.gestorpedidos.pedidos.infraestructura.entrada.rest;

import java.net.URI;
import java.util.List;

import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.ActualizarPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.ConsultarPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.CrearPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.EliminarPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.ListarPedidosPropios;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador de entrada: toda operacion y toda identidad pasan por los casos de uso. */
@RestController
@Profile("persistencia")
@RequestMapping(path = "/api/v1/pedidos", produces = MediaType.APPLICATION_JSON_VALUE)
public class PedidosControlador {

    private final CrearPedido crear;
    private final ListarPedidosPropios listar;
    private final ConsultarPedido consultar;
    private final ActualizarPedido actualizar;
    private final EliminarPedido eliminar;

    public PedidosControlador(CrearPedido crear, ListarPedidosPropios listar, ConsultarPedido consultar,
            ActualizarPedido actualizar, EliminarPedido eliminar) {
        this.crear = crear;
        this.listar = listar;
        this.consultar = consultar;
        this.actualizar = actualizar;
        this.eliminar = eliminar;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PedidoRespuesta> crear(@Valid @RequestBody PedidoSolicitud solicitud) {
        PedidoRespuesta respuesta = PedidoRespuesta.desde(crear.ejecutar(solicitud.aComando()));
        return ResponseEntity.created(URI.create("/api/v1/pedidos/" + respuesta.id())).body(respuesta);
    }

    @GetMapping
    public List<PedidoRespuesta> listar() {
        return listar.ejecutar().stream().map(PedidoRespuesta::desde).toList();
    }

    @GetMapping("/{id}")
    public PedidoRespuesta consultar(@PathVariable("id") @Positive Long id) {
        return PedidoRespuesta.desde(consultar.ejecutar(id));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PedidoRespuesta actualizar(@PathVariable("id") @Positive Long id,
            @Valid @RequestBody PedidoSolicitud solicitud) {
        return PedidoRespuesta.desde(actualizar.ejecutar(id, solicitud.aComando()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable("id") @Positive Long id) {
        eliminar.ejecutar(id);
        return ResponseEntity.noContent().build();
    }
}
