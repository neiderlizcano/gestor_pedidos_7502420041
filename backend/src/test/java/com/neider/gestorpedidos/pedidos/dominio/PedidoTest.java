package com.neider.gestorpedidos.pedidos.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PedidoTest {

    private static final UUID PROPIETARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTRO_USUARIO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate FECHA = LocalDate.of(2026, 1, 10);
    private static final LocalDate FECHA_ENVIO = LocalDate.of(2026, 1, 11);
    private static final LocalDate FECHA_ENTREGA = LocalDate.of(2026, 1, 12);

    @Test
    void creaPedidoValidoConIdentificador() {
        Pedido pedido = pedido().crear();

        assertThat(pedido.id()).isEqualTo(15L);
        assertThat(pedido.usuarioId()).isEqualTo(PROPIETARIO);
        assertThat(pedido.fecha()).isEqualTo(FECHA);
        assertThat(pedido.fechaEnvio()).isEqualTo(FECHA_ENVIO);
        assertThat(pedido.fechaEntrega()).isEqualTo(FECHA_ENTREGA);
        assertThat(pedido.cliente()).isEqualTo("Cliente");
        assertThat(pedido.proveedor()).isEqualTo("Proveedor");
        assertThat(pedido.valor()).isEqualByComparingTo("100.50");
        assertThat(pedido.estado()).isEqualTo("Pendiente");
        assertThat(pedido.pais()).isEqualTo("Colombia");
        assertThat(pedido.departamento()).isEqualTo("Santander");
        assertThat(pedido.ciudad()).isEqualTo("Bucaramanga");
        assertThat(pedido.nomenclaturaVivienda()).isEqualTo("Calle 1 #2-3");
        assertThat(pedido.propina()).isEqualByComparingTo("2.00");
    }

    @Test
    void permiteIdentificadorPendienteAntesDePersistir() {
        Pedido pedido = pedido().id(null).crear();

        assertThat(pedido.id()).isNull();
        assertThat(pedido.usuarioId()).isEqualTo(PROPIETARIO);
    }

    @Test
    void rechazaPropietarioNulo() {
        assertThatThrownBy(() -> pedido().usuarioId(null).crear())
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("propietario");
    }

    static Stream<Arguments> camposDeTextoObligatorios() {
        return Stream.of(
                arguments("cliente", (BiConsumer<PedidoBuilder, String>) PedidoBuilder::cliente),
                arguments("proveedor", (BiConsumer<PedidoBuilder, String>) PedidoBuilder::proveedor),
                arguments("estado", (BiConsumer<PedidoBuilder, String>) PedidoBuilder::estado),
                arguments("pais", (BiConsumer<PedidoBuilder, String>) PedidoBuilder::pais),
                arguments("departamento", (BiConsumer<PedidoBuilder, String>) PedidoBuilder::departamento),
                arguments("ciudad", (BiConsumer<PedidoBuilder, String>) PedidoBuilder::ciudad),
                arguments("nomenclatura_vivienda",
                        (BiConsumer<PedidoBuilder, String>) PedidoBuilder::nomenclaturaVivienda));
    }

    @ParameterizedTest(name = "rechaza {0} nulo o en blanco")
    @MethodSource("camposDeTextoObligatorios")
    void rechazaCamposDeTextoObligatorios(String campo, BiConsumer<PedidoBuilder, String> setter) {
        PedidoBuilder nulo = pedido();
        setter.accept(nulo, null);
        assertThatThrownBy(nulo::crear)
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining(campo);

        PedidoBuilder blanco = pedido();
        setter.accept(blanco, "   ");
        assertThatThrownBy(blanco::crear)
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining(campo);
    }

    @ParameterizedTest(name = "rechaza fecha {0} nula")
    @ValueSource(strings = {"fecha", "fecha_envio", "fecha_entrega"})
    void rechazaFechasNulas(String campo) {
        PedidoBuilder builder = pedido();
        switch (campo) {
            case "fecha" -> builder.fecha(null);
            case "fecha_envio" -> builder.fechaEnvio(null);
            default -> builder.fechaEntrega(null);
        }

        assertThatThrownBy(builder::crear)
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining(campo);
    }

    @Test
    void aceptaFechasIgualesEnElLimite() {
        Pedido pedido = pedido()
                .fecha(FECHA)
                .fechaEnvio(FECHA)
                .fechaEntrega(FECHA)
                .crear();

        assertThat(pedido.fechaEnvio()).isEqualTo(pedido.fecha());
        assertThat(pedido.fechaEntrega()).isEqualTo(pedido.fechaEnvio());
    }

    @Test
    void rechazaFechaDeEnvioAnteriorALaFechaDelPedido() {
        assertThatThrownBy(() -> pedido().fechaEnvio(FECHA.minusDays(1)).crear())
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("fecha de envio");
    }

    @Test
    void rechazaFechaDeEntregaAnteriorALaFechaDeEnvio() {
        assertThatThrownBy(() -> pedido().fechaEntrega(FECHA_ENVIO.minusDays(1)).crear())
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("fecha de entrega");
    }

    @Test
    void rechazaValorNulo() {
        assertThatThrownBy(() -> pedido().valor(null).crear())
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("valor");
    }

    @ParameterizedTest(name = "rechaza valor no positivo {0}")
    @ValueSource(strings = {"0", "0.00", "-0.01", "-100.00"})
    void rechazaValorNoPositivo(String valor) {
        assertThatThrownBy(() -> pedido().valor(new BigDecimal(valor)).crear())
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("mayor que cero");
    }

    @Test
    void aceptaValorMinimoPositivo() {
        Pedido pedido = pedido().valor(new BigDecimal("0.01")).crear();

        assertThat(pedido.valor()).isEqualByComparingTo("0.01");
        assertThat(pedido.valor().scale()).isEqualTo(2);
    }

    @Test
    void aceptaValorConDosDecimales() {
        Pedido pedido = pedido().valor(new BigDecimal("1234.56")).crear();

        assertThat(pedido.valor()).isEqualByComparingTo("1234.56");
    }

    @ParameterizedTest(name = "rechaza importe con mas de dos decimales {0}")
    @ValueSource(strings = {"1.234", "0.001", "100.999"})
    void rechazaImportesConMasDeDosDecimales(String valor) {
        assertThatThrownBy(() -> pedido().valor(new BigDecimal(valor)).crear())
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("dos decimales");
    }

    @Test
    void normalizaCerosFinalesSinRedondear() {
        Pedido pedido = pedido()
                .valor(new BigDecimal("10.500"))
                .propina(new BigDecimal("1.500"))
                .crear();

        assertThat(pedido.valor()).isEqualByComparingTo("10.50");
        assertThat(pedido.valor().scale()).isEqualTo(2);
        assertThat(pedido.propina()).isEqualByComparingTo("1.50");
        assertThat(pedido.propina().scale()).isEqualTo(2);
    }

    @Test
    void rechazaImporteQueExcedeLaPrecisionDeLaColumna() {
        assertThatThrownBy(() -> pedido().valor(new BigDecimal("10000000000.00")).crear())
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("NUMERIC(12,2)");
    }

    @Test
    void aceptaElMaximoImportePermitidoPorLaColumna() {
        Pedido pedido = pedido().valor(new BigDecimal("9999999999.99")).crear();

        assertThat(pedido.valor()).isEqualByComparingTo("9999999999.99");
    }

    @Test
    void rechazaPropinaNula() {
        assertThatThrownBy(() -> pedido().propina(null).crear())
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("propina");
    }

    @Test
    void aceptaPropinaCero() {
        Pedido pedido = pedido().propina(BigDecimal.ZERO).crear();

        assertThat(pedido.propina()).isEqualByComparingTo("0.00");
        assertThat(pedido.propina().scale()).isEqualTo(2);
    }

    @ParameterizedTest(name = "rechaza propina negativa {0}")
    @ValueSource(strings = {"-0.01", "-5.00"})
    void rechazaPropinaNegativa(String propina) {
        assertThatThrownBy(() -> pedido().propina(new BigDecimal(propina)).crear())
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    void rechazaPropinaConMasDeDosDecimales() {
        assertThatThrownBy(() -> pedido().propina(new BigDecimal("0.001")).crear())
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("dos decimales");
    }

    @Test
    void asignaIdentificadorAlPedidoPendiente() {
        Pedido pendiente = pedido().id(null).crear();

        Pedido persistido = pendiente.asignarId(42L);

        assertThat(persistido.id()).isEqualTo(42L);
        assertThat(persistido.usuarioId()).isEqualTo(PROPIETARIO);
        assertThat(pendiente.id()).isNull();
    }

    @Test
    void rechazaIdentificadorNuloAlAsignar() {
        Pedido pendiente = pedido().id(null).crear();

        assertThatThrownBy(() -> pendiente.asignarId(null))
                .isInstanceOf(PedidoInvalidoException.class);
    }

    @Test
    void rechazaCambiarUnIdentificadorYaAsignado() {
        Pedido pedido = pedido().crear();

        assertThatThrownBy(() -> pedido.asignarId(99L))
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("ya tiene un identificador");
    }

    @Test
    void actualizaDatosConservandoIdentificadorYPropietario() {
        Pedido original = pedido().crear();
        Pedido cambios = pedido()
                .cliente("Nuevo cliente")
                .valor(new BigDecimal("250.00"))
                .propina(new BigDecimal("10.00"))
                .crear();

        Pedido actualizado = original.actualizarCon(cambios);

        assertThat(actualizado.id()).isEqualTo(original.id());
        assertThat(actualizado.usuarioId()).isEqualTo(PROPIETARIO);
        assertThat(actualizado.cliente()).isEqualTo("Nuevo cliente");
        assertThat(actualizado.valor()).isEqualByComparingTo("250.00");
        assertThat(original.cliente()).isEqualTo("Cliente");
    }

    @Test
    void rechazaActualizacionConOtroPropietario() {
        Pedido original = pedido().crear();
        Pedido ajeno = pedido().usuarioId(OTRO_USUARIO).crear();

        assertThatThrownBy(() -> original.actualizarCon(ajeno))
                .isInstanceOf(PropietarioInmutableException.class)
                .hasMessageContaining("propietario");
    }

    @Test
    void rechazaActualizacionSinDatos() {
        Pedido original = pedido().crear();

        assertThatThrownBy(() -> original.actualizarCon(null))
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("actualizacion");
    }

    @Test
    void actualizarNoPermiteCambiarIdentificadorNiPropietario() {
        Pedido original = pedido().crear();
        Pedido cambios = pedido()
                .id(99L)
                .cliente("Nuevo cliente")
                .crear();

        Pedido actualizado = original.actualizarCon(cambios);

        assertThat(actualizado.id()).isEqualTo(15L);
        assertThat(actualizado.usuarioId()).isEqualTo(PROPIETARIO);
        assertThat(actualizado.cliente()).isEqualTo("Nuevo cliente");
    }

    @Test
    void validaReglasAlActualizar() {
        Pedido original = pedido().crear();

        assertThatThrownBy(() -> original.actualizarCon(pedido().valor(BigDecimal.ZERO).crear()))
                .isInstanceOf(PedidoInvalidoException.class);
    }

    @Test
    void reconoceAlPropietarioDelPedido() {
        Pedido pedido = pedido().crear();

        assertThat(pedido.perteneceA(PROPIETARIO)).isTrue();
        assertThat(pedido.perteneceA(OTRO_USUARIO)).isFalse();
        assertThat(pedido.perteneceA(null)).isFalse();
    }

    private static PedidoBuilder pedido() {
        return new PedidoBuilder();
    }

    private static final class PedidoBuilder {

        private Long id = 15L;
        private UUID usuarioId = PROPIETARIO;
        private LocalDate fecha = FECHA;
        private LocalDate fechaEnvio = FECHA_ENVIO;
        private LocalDate fechaEntrega = FECHA_ENTREGA;
        private String cliente = "Cliente";
        private String proveedor = "Proveedor";
        private BigDecimal valor = new BigDecimal("100.50");
        private String estado = "Pendiente";
        private String pais = "Colombia";
        private String departamento = "Santander";
        private String ciudad = "Bucaramanga";
        private String nomenclaturaVivienda = "Calle 1 #2-3";
        private BigDecimal propina = new BigDecimal("2.00");

        PedidoBuilder id(Long valor) {
            this.id = valor;
            return this;
        }

        PedidoBuilder usuarioId(UUID valor) {
            this.usuarioId = valor;
            return this;
        }

        PedidoBuilder fecha(LocalDate valor) {
            this.fecha = valor;
            return this;
        }

        PedidoBuilder fechaEnvio(LocalDate valor) {
            this.fechaEnvio = valor;
            return this;
        }

        PedidoBuilder fechaEntrega(LocalDate valor) {
            this.fechaEntrega = valor;
            return this;
        }

        PedidoBuilder cliente(String valor) {
            this.cliente = valor;
            return this;
        }

        PedidoBuilder proveedor(String valor) {
            this.proveedor = valor;
            return this;
        }

        PedidoBuilder valor(BigDecimal valor) {
            this.valor = valor;
            return this;
        }

        PedidoBuilder estado(String valor) {
            this.estado = valor;
            return this;
        }

        PedidoBuilder pais(String valor) {
            this.pais = valor;
            return this;
        }

        PedidoBuilder departamento(String valor) {
            this.departamento = valor;
            return this;
        }

        PedidoBuilder ciudad(String valor) {
            this.ciudad = valor;
            return this;
        }

        PedidoBuilder nomenclaturaVivienda(String valor) {
            this.nomenclaturaVivienda = valor;
            return this;
        }

        PedidoBuilder propina(BigDecimal valor) {
            this.propina = valor;
            return this;
        }

        Pedido crear() {
            return new Pedido(id, usuarioId, fecha, fechaEnvio, fechaEntrega, cliente, proveedor,
                    valor, estado, pais, departamento, ciudad, nomenclaturaVivienda, propina);
        }
    }
}
