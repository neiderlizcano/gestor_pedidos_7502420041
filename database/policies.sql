-- POLÍTICAS RLS - TABLA USUARIOS
-- Gestor de Pedidos - Ejercicio 17

-- Eliminar políticas anteriores si existen
DROP POLICY IF EXISTS "usuarios_select_own" ON public.usuarios;
DROP POLICY IF EXISTS "usuarios_insert_own" ON public.usuarios;
DROP POLICY IF EXISTS "usuarios_update_own" ON public.usuarios;
DROP POLICY IF EXISTS "usuarios_delete_own" ON public.usuarios;


-- CONSULTAR
-- Cada usuario solo puede consultar su propio perfil.
CREATE POLICY "usuarios_select_own"
ON public.usuarios
FOR SELECT
TO authenticated
USING ((SELECT auth.uid()) = id);


-- INSERTAR
-- Cada usuario solo puede crear el perfil correspondiente a su ID.
CREATE POLICY "usuarios_insert_own"
ON public.usuarios
FOR INSERT
TO authenticated
WITH CHECK ((SELECT auth.uid()) = id);


-- ACTUALIZAR
-- Cada usuario solo puede actualizar su propio perfil.
CREATE POLICY "usuarios_update_own"
ON public.usuarios
FOR UPDATE
TO authenticated
USING ((SELECT auth.uid()) = id)
WITH CHECK ((SELECT auth.uid()) = id);


-- ELIMINAR
-- Cada usuario solo puede eliminar su propio perfil.
CREATE POLICY "usuarios_delete_own"
ON public.usuarios
FOR DELETE
TO authenticated
USING ((SELECT auth.uid()) = id);