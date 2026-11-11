package com.neider.gestorpedidos.usuarios.infraestructura.salida.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** JDBC simulado y datos sinteticos: no se abre ninguna conexion. */
@ExtendWith(MockitoExtension.class)
class RepositorioUsuariosJdbcTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTRO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Usuario PERFIL = new Usuario(ID, "usuario_uno", "Usuario Uno", "uno@example.invalid");

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private EjecutorRlsJdbc rls;

    private RepositorioUsuariosJdbc repositorio;

    @BeforeEach
    void crearAdaptador() {
        lenient().when(rls.ejecutar(any(UUID.class), any())).thenAnswer(invocacion -> {
            Supplier<?> operacion = invocacion.getArgument(1);
            return operacion.get();
        });
        repositorio = new RepositorioUsuariosJdbc(jdbc, rls);
    }

    @Test
    void crearUsaElUuidYCorreoRecibidosSinGenerarIdentidadNiSobrescribir() throws SQLException {
        devolverFila(PERFIL);

        assertThat(repositorio.crear(PERFIL)).contains(PERFIL);

        Consulta consulta = capturarConsulta();
        assertThat(consulta.sql()).contains("INSERT INTO public.usuarios (id, username, nombre, email)",
                "VALUES (:id, :username, :nombre, :email)", "ON CONFLICT DO NOTHING",
                "RETURNING id, username, nombre, email")
                .doesNotContain("DO UPDATE", "auth.users", "password", ID.toString(), PERFIL.email());
        comprobarDatosEditables(consulta.parametros(), PERFIL);
        assertThat(consulta.parametros().getValue("email")).isEqualTo(PERFIL.email());
    }

    @ParameterizedTest
    @ValueSource(strings = {"uuid", "email"})
    void crearDevuelveVacioAnteConflictoSinConsultarElPerfilExistente(String campoDuplicado) {
        Usuario intento = "uuid".equals(campoDuplicado)
                ? new Usuario(ID, "otro_username", "Otro Nombre", "nuevo@example.invalid")
                : new Usuario(OTRO_ID, "otro_username", "Otro Nombre", PERFIL.email());
        when(jdbc.query(anyString(), any(SqlParameterSource.class), mapper())).thenReturn(List.of());

        assertThat(repositorio.crear(intento)).isEmpty();

        Consulta consulta = capturarConsulta();
        assertThat(consulta.sql()).contains("ON CONFLICT DO NOTHING").doesNotContain("SELECT", "DO UPDATE");
        assertThat(consulta.parametros().getValue("id")).isEqualTo(intento.id());
        assertThat(consulta.parametros().getValue("email")).isEqualTo(intento.email());
    }

    @Test
    void crearPropagaViolacionDeFkSinConfundirlaConUnDuplicado() {
        DataIntegrityViolationException error = new DataIntegrityViolationException("FK sintetica");
        when(jdbc.query(anyString(), any(SqlParameterSource.class), mapper())).thenThrow(error);

        assertThatThrownBy(() -> repositorio.crear(PERFIL)).isSameAs(error);
    }

    @Test
    void buscarFiltraPorUuidYMapeaTodasLasColumnas() throws SQLException {
        devolverFila(PERFIL);

        assertThat(repositorio.buscarPorId(ID)).contains(PERFIL);

        Consulta consulta = capturarConsulta();
        assertThat(consulta.sql()).contains("SELECT id, username, nombre, email",
                "FROM public.usuarios", "WHERE id = :id").doesNotContain(ID.toString());
        assertThat(consulta.parametros().getParameterNames()).containsExactly("id");
        assertThat(consulta.parametros().getValue("id")).isEqualTo(ID).isInstanceOf(UUID.class);
    }

    @Test
    void buscarDevuelveVacioSiElPerfilNoExiste() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), mapper())).thenReturn(List.of());

        assertThat(repositorio.buscarPorId(ID)).isEmpty();
    }

    @Test
    void noBuscaOtroUuidNiUsaElCorreoComoAlternativaAlNoEncontrarElPerfil() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), mapper()))
                .thenAnswer(invocacion -> {
                    SqlParameterSource parametros = invocacion.getArgument(1);
                    return OTRO_ID.equals(parametros.getValue("id")) ? List.of(PERFIL) : List.of();
                });

        assertThat(repositorio.buscarPorId(ID)).isEmpty();

        Consulta consulta = capturarConsulta();
        assertThat(consulta.parametros().getValue("id")).isEqualTo(ID);
        assertThat(consulta.sql()).contains("WHERE id = :id").doesNotContain(" OR ");
    }

    @Test
    void actualizarSoloModificaUsernameYNombreYDevuelveElCorreoAlmacenado() throws SQLException {
        Usuario cambios = new Usuario(ID, "nuevo_username", "Nuevo Nombre", "ignorado@example.invalid");
        Usuario almacenado = new Usuario(ID, cambios.username(), cambios.nombre(), PERFIL.email());
        devolverFila(almacenado);

        assertThat(repositorio.actualizar(cambios)).contains(almacenado);

        Consulta consulta = capturarConsulta();
        assertThat(consulta.sql()).contains("UPDATE public.usuarios", "WHERE id = :id",
                "RETURNING id, username, nombre, email");
        String asignaciones = consulta.sql().substring(consulta.sql().indexOf("SET"),
                consulta.sql().indexOf("WHERE"));
        assertThat(asignaciones).contains("username = :username", "nombre = :nombre")
                .doesNotContain("id", "email");
        comprobarDatosEditables(consulta.parametros(), cambios);
        assertThat(consulta.parametros().hasValue("email")).isFalse();
    }

    @Test
    void actualizarDevuelveVacioSinInsertarSiNoExisteElUuid() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), mapper())).thenReturn(List.of());

        assertThat(repositorio.actualizar(PERFIL)).isEmpty();

        Consulta consulta = capturarConsulta();
        assertThat(consulta.sql()).contains("WHERE id = :id").doesNotContain("INSERT", "ON CONFLICT");
        assertThat(consulta.parametros().getValue("id")).isEqualTo(ID);
    }

    @Test
    void actualizarNoSeleccionaUnPerfilDistintoPorCorreoONombre() {
        Usuario cambios = new Usuario(ID, "otro_username", "Otro Nombre", "otro@example.invalid");
        when(jdbc.query(anyString(), any(SqlParameterSource.class), mapper())).thenReturn(List.of());

        assertThat(repositorio.actualizar(cambios)).isEmpty();

        Consulta consulta = capturarConsulta();
        assertThat(consulta.sql()).contains("WHERE id = :id").doesNotContain(" OR ");
        assertThat(consulta.parametros().getValue("id")).isEqualTo(ID).isNotEqualTo(OTRO_ID);
        assertThat(consulta.parametros().hasValue("email")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"crear", "buscar", "actualizar"})
    void propagaErroresJdbcSinPresentarlosComoAusenciaOConflicto(String operacion) {
        DataAccessResourceFailureException error = new DataAccessResourceFailureException("Error sintetico");
        when(jdbc.query(anyString(), any(SqlParameterSource.class), mapper())).thenThrow(error);

        assertThatThrownBy(() -> ejecutar(operacion, PERFIL)).isSameAs(error);
    }

    @ParameterizedTest
    @ValueSource(strings = {"crear", "actualizar"})
    void datosConSintaxisSqlSeEnvianSoloComoParametros(String operacion) {
        String texto = "O'Connor'; DROP TABLE public.usuarios; --";
        Usuario perfil = new Usuario(ID, texto, texto, "uno@example.invalid");
        when(jdbc.query(anyString(), any(SqlParameterSource.class), mapper())).thenReturn(List.of(perfil));

        ejecutar(operacion, perfil);

        Consulta consulta = capturarConsulta();
        assertThat(consulta.sql()).doesNotContain(texto);
        comprobarDatosEditables(consulta.parametros(), perfil);
    }

    @Test
    void rechazaParametrosObligatoriosNulosAntesDeUsarJdbc() {
        assertThatThrownBy(() -> repositorio.crear(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repositorio.buscarPorId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repositorio.actualizar(null)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(jdbc);
    }

    private void ejecutar(String operacion, Usuario perfil) {
        switch (operacion) {
            case "crear" -> repositorio.crear(perfil);
            case "buscar" -> repositorio.buscarPorId(perfil.id());
            case "actualizar" -> repositorio.actualizar(perfil);
            default -> throw new IllegalArgumentException("Operacion de prueba desconocida.");
        }
    }

    private void devolverFila(Usuario perfil) throws SQLException {
        ResultSet resultado = mock(ResultSet.class);
        when(resultado.getObject("id", UUID.class)).thenReturn(perfil.id());
        when(resultado.getString("username")).thenReturn(perfil.username());
        when(resultado.getString("nombre")).thenReturn(perfil.nombre());
        when(resultado.getString("email")).thenReturn(perfil.email());
        when(jdbc.query(anyString(), any(SqlParameterSource.class), mapper()))
                .thenAnswer(invocacion -> {
                    RowMapper<Usuario> mapper = invocacion.getArgument(2);
                    return List.of(mapper.mapRow(resultado, 0));
                });
    }

    private Consulta capturarConsulta() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parametros.capture(), mapper());
        verifyNoMoreInteractions(jdbc);
        return new Consulta(sql.getValue(), parametros.getValue());
    }

    private static RowMapper<Usuario> mapper() {
        return ArgumentMatchers.<RowMapper<Usuario>>any();
    }

    private static void comprobarDatosEditables(SqlParameterSource parametros, Usuario perfil) {
        assertThat(parametros.getValue("id")).isEqualTo(perfil.id()).isInstanceOf(UUID.class);
        assertThat(parametros.getValue("username")).isEqualTo(perfil.username());
        assertThat(parametros.getValue("nombre")).isEqualTo(perfil.nombre());
    }

    private record Consulta(String sql, SqlParameterSource parametros) {
    }
}
