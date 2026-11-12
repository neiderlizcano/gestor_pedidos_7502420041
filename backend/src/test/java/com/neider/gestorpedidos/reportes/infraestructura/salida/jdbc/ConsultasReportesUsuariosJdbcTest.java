package com.neider.gestorpedidos.reportes.infraestructura.salida.jdbc;

import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.ID;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_ID;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.perfil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc;
import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.CampoPerfil;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.CoincidenciaPerfil;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroCorreo;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroNombreUsername;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Verifica SQL y parametros sin abrir conexiones ni simular politicas PostgreSQL. */
class ConsultasReportesUsuariosJdbcTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final EjecutorRlsJdbc rls = mock(EjecutorRlsJdbc.class);
    private final ConsultasReportesUsuariosJdbc consultas = new ConsultasReportesUsuariosJdbc(jdbc, rls);

    @AfterEach
    void limpiarIdentidad() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"NOMBRE,nombre", "USERNAME,username"})
    void coincidenciaExactaUsaSoloColumnasPermitidasYValoresLigados(CampoPerfil campo, String columna) {
        habilitarEjecutor();
        String termino = "x%'_!\\ OR 1=1 --";
        consultas.porNombreUsername(ID, new FiltroNombreUsername(campo, termino, CoincidenciaPerfil.EXACTA));

        var sql = ArgumentCaptor.forClass(String.class);
        var parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parametros.capture(), ArgumentMatchers.<RowMapper<Usuario>>any());
        assertThat(sql.getValue()).isEqualTo("SELECT id, username, nombre, email FROM public.usuarios WHERE id = :usuarioId AND "
                + columna + " = :termino").doesNotContain(termino, ID.toString(), "LIKE", " OR ");
        assertThat(parametros.getValue().getValue("termino")).isEqualTo(termino);
        assertThat(parametros.getValue().getValue("usuarioId")).isEqualTo(ID);
        verify(rls).ejecutar(eq(ID), any());
    }

    static Stream<Arguments> prefijosLiterales() {
        return Stream.of(
                Arguments.of("Ana", "Ana%"),
                Arguments.of("%", "!%%"),
                Arguments.of("_", "!_%"),
                Arguments.of("!", "!!%"),
                Arguments.of("\\", "\\%"),
                Arguments.of("Ana%_!\\", "Ana!%!_!!\\%"),
                Arguments.of("x' OR 1=1 --", "x' OR 1=1 --%"))
                .flatMap(par -> Stream.of(CampoPerfil.NOMBRE, CampoPerfil.USERNAME)
                        .map(campo -> Arguments.of(campo, par.get()[0], par.get()[1])));
    }

    @ParameterizedTest
    @MethodSource("prefijosLiterales")
    void prefijoEscapaComodinesYCaracteresDeEscapeComoTextoLiteral(CampoPerfil campo, String termino, String patron) {
        habilitarEjecutor();
        consultas.porNombreUsername(ID, new FiltroNombreUsername(campo, termino, CoincidenciaPerfil.PREFIJO));

        var sql = ArgumentCaptor.forClass(String.class);
        var parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parametros.capture(), ArgumentMatchers.<RowMapper<Usuario>>any());
        String columna = campo == CampoPerfil.NOMBRE ? "nombre" : "username";
        assertThat(sql.getValue()).isEqualTo("SELECT id, username, nombre, email FROM public.usuarios WHERE id = :usuarioId AND "
                + columna + " LIKE :termino ESCAPE '!'");
        assertThat(parametros.getValue().getValue("usuarioId")).isEqualTo(ID);
        assertThat(parametros.getValue().getValue("termino")).isEqualTo(patron);
        verify(rls).ejecutar(eq(ID), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"persona@example.com", "persona%_!@example.com", "x' OR 1=1 --@example.com"})
    void correoSiempreEsIgualdadParametrizadaDelPerfilPropio(String correo) {
        habilitarEjecutor();
        consultas.porCorreo(ID, new FiltroCorreo(correo));
        var sql = ArgumentCaptor.forClass(String.class);
        var parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parametros.capture(), ArgumentMatchers.<RowMapper<Usuario>>any());
        assertThat(sql.getValue()).isEqualTo("SELECT id, username, nombre, email FROM public.usuarios WHERE id = :usuarioId AND email = :correo")
                .doesNotContain(correo, ID.toString(), "LIKE", " OR ");
        assertThat(parametros.getValue().getValue("correo")).isEqualTo(correo);
        assertThat(parametros.getValue().getValue("usuarioId")).isEqualTo(ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombre", "correo"})
    void consultaYMapeoSoloSeEjecutanDentroDeRls(String reporte) throws SQLException {
        var dentro = new AtomicBoolean();
        when(rls.ejecutar(any(UUID.class), any())).thenAnswer(invocacion -> {
            dentro.set(true);
            try {
                return ((Supplier<?>) invocacion.getArgument(1)).get();
            } finally {
                dentro.set(false);
            }
        });
        ResultSet fila = mock(ResultSet.class);
        when(fila.getObject("id", UUID.class)).thenReturn(ID);
        when(fila.getString("username")).thenReturn(perfil().username());
        when(fila.getString("nombre")).thenReturn(perfil().nombre());
        when(fila.getString("email")).thenReturn(EMAIL);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Usuario>>any()))
                .thenAnswer(invocacion -> {
                    assertThat(dentro.get()).isTrue();
                    RowMapper<Usuario> mapper = invocacion.getArgument(2);
                    return List.of(mapper.mapRow(fila, 0));
                });

        assertThat(ejecutar(consultas, reporte)).containsExactly(perfil());
        assertThat(dentro.get()).isFalse();
        verify(rls).verificarDataSource(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombre", "correo"})
    void noHayConsultaSiElEjecutorRechaza(String reporte) {
        when(rls.ejecutar(any(UUID.class), any())).thenThrow(new AccessDeniedException("Rechazo sintetico"));
        assertThatThrownBy(() -> ejecutar(consultas, reporte)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombre", "correo"})
    void ejecutorRealBloqueaSinJwtYConUuidDiscordanteSinAdquirirConexion(String reporte) {
        var pool = mock(HikariDataSource.class);
        var plantilla = new NamedParameterJdbcTemplate(pool);
        var ejecutor = new EjecutorRlsJdbc(new UsuarioAutenticado(), new JdbcTransactionManager(pool), plantilla);
        var adaptador = new ConsultasReportesUsuariosJdbc(plantilla, ejecutor);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> ejecutar(adaptador, reporte)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        Jwt jwt = Jwt.withTokenValue("identidad-sintetica-no-es-jwt").header("alg", "ES256").subject(OTRO_ID.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        assertThatThrownBy(() -> ejecutar(adaptador, reporte)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(pool);
    }

    @Test
    void rechazaDataSourceDistintoConElEjecutorReal() {
        var pool = mock(HikariDataSource.class);
        var plantilla = new NamedParameterJdbcTemplate(pool);
        var ejecutor = new EjecutorRlsJdbc(new UsuarioAutenticado(), new JdbcTransactionManager(pool), plantilla);
        assertThatThrownBy(() -> new ConsultasReportesUsuariosJdbc(new NamedParameterJdbcTemplate(mock(HikariDataSource.class)), ejecutor))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(pool);
    }

    private void habilitarEjecutor() {
        when(rls.ejecutar(any(UUID.class), any())).thenAnswer(invocacion -> ((Supplier<?>) invocacion.getArgument(1)).get());
    }

    private static List<Usuario> ejecutar(ConsultasReportesUsuariosJdbc adaptador, String reporte) {
        return "nombre".equals(reporte)
                ? adaptador.porNombreUsername(ID, new FiltroNombreUsername(CampoPerfil.NOMBRE, "Neider", CoincidenciaPerfil.PREFIJO))
                : adaptador.porCorreo(ID, new FiltroCorreo(EMAIL));
    }
}
