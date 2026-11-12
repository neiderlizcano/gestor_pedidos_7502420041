package com.neider.gestorpedidos.usuarios.infraestructura.entrada.rest;

import java.net.URI;
import java.util.Map;

import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.ActualizarPerfil;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.ConsultarPerfilPropio;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.CrearPerfil;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Solo el perfil propio; no recibe UUID ni correo como parametros de identidad. */
@RestController
@Profile("persistencia")
@RequestMapping(path = "/api/v1/usuarios", produces = MediaType.APPLICATION_JSON_VALUE)
public class UsuariosControlador {

    private final CrearPerfil crear;
    private final ConsultarPerfilPropio consultar;
    private final ActualizarPerfil actualizar;

    public UsuariosControlador(CrearPerfil crear, ConsultarPerfilPropio consultar, ActualizarPerfil actualizar) {
        this.crear = crear;
        this.consultar = consultar;
        this.actualizar = actualizar;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PerfilRespuesta> crear(@Valid @RequestBody PerfilSolicitud solicitud,
            @RequestParam Map<String, String> parametros) throws ServletRequestBindingException {
        exigirSinParametros(parametros);
        PerfilRespuesta respuesta = PerfilRespuesta.desde(crear.ejecutar(solicitud.aComando()));
        return ResponseEntity.created(URI.create("/api/v1/usuarios/me")).body(respuesta);
    }

    @GetMapping("/me")
    public PerfilRespuesta consultar(@RequestParam Map<String, String> parametros) throws ServletRequestBindingException {
        exigirSinParametros(parametros);
        return PerfilRespuesta.desde(consultar.ejecutar());
    }

    @PutMapping(path = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PerfilRespuesta actualizar(@Valid @RequestBody PerfilSolicitud solicitud,
            @RequestParam Map<String, String> parametros) throws ServletRequestBindingException {
        exigirSinParametros(parametros);
        return PerfilRespuesta.desde(actualizar.ejecutar(solicitud.aComando()));
    }

    private static void exigirSinParametros(Map<String, String> parametros) throws ServletRequestBindingException {
        if (!parametros.isEmpty()) {
            throw new ServletRequestBindingException("El perfil propio no admite parametros de consulta.");
        }
    }
}
