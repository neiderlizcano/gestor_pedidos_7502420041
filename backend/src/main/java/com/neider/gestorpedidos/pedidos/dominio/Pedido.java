package com.neider.gestorpedidos.pedidos.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pedido almacenado en {@code public.pedidos}.
 *
 * <p>El identificador puede ser nulo mientras el pedido aun no se ha persistido.
 * El propietario es obligatorio y no puede modificarse.</p>
 */
public record Pedido(
        Long id,
        UUID usuarioId,
        LocalDate fecha,
        LocalDate fechaEnvio,
        LocalDate fechaEntrega,
        String cliente,
        String proveedor,
        BigDecimal valor,
        String estado,
        String pais,
        String departamento,
        String ciudad,
        String nomenclaturaVivienda,
        BigDecimal propina) {

    /** Escala de {@code NUMERIC(12,2)}. */
    private static final int ESCALA_MONETARIA = 2;

    /** Precision total de {@code NUMERIC(12,2)}. */
    private static final int PRECISION_MONETARIA = 12;

    public Pedido {
        if (usuarioId == null) {
            throw new PedidoInvalidoException("El propietario del pedido es obligatorio.");
        }

        fecha = fechaObligatoria(fecha, "fecha");
        fechaEnvio = fechaObligatoria(fechaEnvio, "fecha_envio");
        fechaEntrega = fechaObligatoria(fechaEntrega, "fecha_entrega");

        cliente = textoObligatorio(cliente, "cliente");
        proveedor = textoObligatorio(proveedor, "proveedor");
        estado = textoObligatorio(estado, "estado");
        pais = textoObligatorio(pais, "pais");
        departamento = textoObligatorio(departamento, "departamento");
        ciudad = textoObligatorio(ciudad, "ciudad");
        nomenclaturaVivienda = textoObligatorio(nomenclaturaVivienda, "nomenclatura_vivienda");

        valor = importe(valor, "valor", true);
        propina = importe(propina, "propina", false);

        if (fechaEnvio.isBefore(fecha)) {
            throw new PedidoInvalidoException("La fecha de envio no puede ser anterior a la fecha del pedido.");
        }
        if (fechaEntrega.isBefore(fechaEnvio)) {
            throw new PedidoInvalidoException("La fecha de entrega no puede ser anterior a la fecha de envio.");
        }
    }

    /**
     * Asigna el identificador generado por la base de datos a un pedido pendiente.
     */
    public Pedido asignarId(Long nuevoId) {
        if (nuevoId == null) {
            throw new PedidoInvalidoException("El identificador asignado no puede ser nulo.");
        }
        if (id != null && !id.equals(nuevoId)) {
            throw new PedidoInvalidoException("El pedido ya tiene un identificador asignado.");
        }
        return new Pedido(nuevoId, usuarioId, fecha, fechaEnvio, fechaEntrega, cliente,
                proveedor, valor, estado, pais, departamento, ciudad, nomenclaturaVivienda, propina);
    }

    /**
     * Devuelve una nueva version con los datos editables del pedido indicado.
     *
     * <p>Conserva el identificador y el propietario de esta instancia. Si los datos
     * recibidos pertenecen a otro propietario se rechaza la operacion.</p>
     */
    public Pedido actualizarCon(Pedido cambios) {
        if (cambios == null) {
            throw new PedidoInvalidoException("Los datos de actualizacion son obligatorios.");
        }
        if (!usuarioId.equals(cambios.usuarioId())) {
            throw new PropietarioInmutableException(
                    "No se puede modificar el propietario de un pedido.");
        }
        return new Pedido(id, usuarioId, cambios.fecha(), cambios.fechaEnvio(), cambios.fechaEntrega(),
                cambios.cliente(), cambios.proveedor(), cambios.valor(), cambios.estado(),
                cambios.pais(), cambios.departamento(), cambios.ciudad(),
                cambios.nomenclaturaVivienda(), cambios.propina());
    }

    /**
     * Indica si el pedido pertenece al usuario indicado.
     */
    public boolean perteneceA(UUID candidato) {
        return usuarioId.equals(candidato);
    }

    private static LocalDate fechaObligatoria(LocalDate valor, String campo) {
        if (valor == null) {
            throw new PedidoInvalidoException("El campo " + campo + " es obligatorio.");
        }
        return valor;
    }

    private static String textoObligatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new PedidoInvalidoException("El campo " + campo + " es obligatorio.");
        }
        return valor;
    }

    private static BigDecimal importe(BigDecimal valor, String campo, boolean estrictamentePositivo) {
        if (valor == null) {
            throw new PedidoInvalidoException("El campo " + campo + " es obligatorio.");
        }

        BigDecimal normalizado;
        try {
            normalizado = valor.setScale(ESCALA_MONETARIA, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new PedidoInvalidoException(
                    "El campo " + campo + " admite maximo dos decimales y no se redondea automaticamente.");
        }

        if (normalizado.precision() > PRECISION_MONETARIA) {
            throw new PedidoInvalidoException(
                    "El campo " + campo + " excede la precision de NUMERIC(12,2).");
        }
        if (estrictamentePositivo && normalizado.signum() <= 0) {
            throw new PedidoInvalidoException("El campo " + campo + " debe ser mayor que cero.");
        }
        if (!estrictamentePositivo && normalizado.signum() < 0) {
            throw new PedidoInvalidoException("El campo " + campo + " no puede ser negativo.");
        }
        return normalizado;
    }
}
