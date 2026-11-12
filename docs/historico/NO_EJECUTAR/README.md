# Historico de trabajo — NO EJECUTAR

`V20261112_01__delete_cuenta_propia.sql.txt` conserva sin cambios el contenido de
la migracion SQL descartada. Nunca se instalo. Su cabecera original no constituye
una instruccion vigente: NO ejecutar ni devolver a un directorio de migraciones.

La unica estrategia activa preparada es la Edge Function `delete-my-account` y
Supabase Auth Admin. No se requieren esquema private, funcion SQL ni grants nuevos.
El adaptador EliminarCuentaJdbc tambien queda fuera del cableado de Spring.
