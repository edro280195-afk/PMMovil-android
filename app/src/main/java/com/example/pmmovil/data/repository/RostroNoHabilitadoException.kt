package com.example.pmmovil.data.repository

/**
 * El servidor contestó que el acceso facial no está habilitado (503).
 *
 * Va tipada y no como texto porque la pantalla de acceso tiene que
 * distinguirla de un "no te reconocí": un rechazo de identidad merece
 * reintentar, pero un módulo apagado no. Gastar los dos intentos contra un
 * servidor que nunca va a contestar que sí es hacerle perder cinco segundos
 * a alguien que sólo quería entrar con su gafete.
 */
class RostroNoHabilitadoException(
    mensaje: String = "El acceso con rostro no está habilitado"
) : Exception(mensaje)
