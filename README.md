# Gestor de Pedidos

Aplicación móvil desarrollada en Kodular para gestionar pedidos de forma segura mediante Supabase.

## Desarrollador

**Neider Lizcano**  
Estudiante de Ingeniería de Software.

## Funcionalidades

- Registro, Login y recuperación de contraseña.
- CRUD completo de pedidos.
- Perfil de usuario.
- Reportes por estado y ciudad.
- Persistencia local con TinyDB.
- Seguridad mediante RLS.
- Location Sensor.
- Accelerometer Sensor.
- Cierre de sesión.

## Tecnologías

- Kodular
- Supabase
- PostgreSQL
- TinyDB
- Git y GitHub

## Base de datos

Tablas principales:

- `usuarios`
- `pedidos`

Cada usuario solo puede acceder a sus propios pedidos mediante políticas RLS.

## Sensores

- `Location Sensor`: obtiene la ubicación actual para los pedidos.
- `Accelerometer Sensor`: permite actualizar pedidos mediante movimiento del dispositivo.

## Estado

Proyecto funcional y probado en dispositivo Android.