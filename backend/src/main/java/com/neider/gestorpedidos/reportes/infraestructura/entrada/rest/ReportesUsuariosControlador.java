package com.neider.gestorpedidos.reportes.infraestructura.entrada.rest;

import java.util.List;
import java.util.Set;

import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.CampoPerfil;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.CoincidenciaPerfil;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPerfilPorCorreo;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPerfilPorNombreUsername;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroCorreo;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroNombreUsername;
import com.neider.gestorpedidos.usuarios.infraestructura.entrada.rest.PerfilRespuesta;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistencia")
@RequestMapping(path = "/api/v1/reportes/usuarios", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReportesUsuariosControlador {

    private static final Set<String> PARAMETROS_NOMBRE = Set.of("campo", "termino", "coincidencia");
    private static final Set<String> PARAMETROS_CORREO = Set.of("correo");
    private final ConsultarPerfilPorNombreUsername nombreUsername;
    private final ConsultarPerfilPorCorreo correo;

    public ReportesUsuariosControlador(ConsultarPerfilPorNombreUsername nombreUsername, ConsultarPerfilPorCorreo correo) {
        this.nombreUsername = nombreUsername;
        this.correo = correo;
    }

    @GetMapping("/nombre-username")
    public List<PerfilRespuesta> porNombreUsername(
            @RequestParam("campo") String campo,
            @RequestParam("termino") @NotBlank @Size(max = FiltroNombreUsername.MAX_TERMINO) String termino,
            @RequestParam("coincidencia") String coincidencia,
            @RequestParam MultiValueMap<String, String> parametros) throws ServletRequestBindingException {
        exigirParametros(parametros, PARAMETROS_NOMBRE);
        return nombreUsername.ejecutar(new FiltroNombreUsername(CampoPerfil.desde(campo), termino,
                CoincidenciaPerfil.desde(coincidencia))).stream().map(ReportesUsuariosControlador::respuesta).toList();
    }

    @GetMapping("/correo")
    public List<PerfilRespuesta> porCorreo(
            @RequestParam("correo") @NotBlank @Email @Size(max = FiltroCorreo.MAX_CORREO) String filtro,
            @RequestParam MultiValueMap<String, String> parametros) throws ServletRequestBindingException {
        exigirParametros(parametros, PARAMETROS_CORREO);
        return correo.ejecutar(new FiltroCorreo(filtro)).stream().map(ReportesUsuariosControlador::respuesta).toList();
    }

    private static PerfilRespuesta respuesta(com.neider.gestorpedidos.usuarios.dominio.Usuario perfil) {
        return new PerfilRespuesta(perfil.id(), perfil.username(), perfil.nombre(), perfil.email());
    }

    private static void exigirParametros(MultiValueMap<String, String> parametros, Set<String> permitidos)
            throws ServletRequestBindingException {
        if (parametros.entrySet().stream().anyMatch(entrada -> !permitidos.contains(entrada.getKey())
                || entrada.getValue().size() != 1 || entrada.getValue().get(0) == null
                || entrada.getValue().get(0).isBlank())) {
            throw new ServletRequestBindingException("Parametros desconocidos o repetidos en el reporte.");
        }
    }
}
