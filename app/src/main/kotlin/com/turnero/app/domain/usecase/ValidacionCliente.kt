package com.turnero.app.domain.usecase

import com.turnero.app.domain.model.ErrorCliente

/**
 * Validación compartida por los tres casos de uso que escriben un `Cliente`.
 *
 * Vive aparte y no como método de los use cases a propósito: si `crear`, `actualizar` y
 * `eliminar` tuvieran cada uno su copia, el día que se agregue una regla nueva seguro se
 * olvide en uno de los tres.
 *
 * Recibe los valores **ya normalizados** (ver [normalizarTextoOpcional]) y por eso puede
 * tratar un `null` como "no informado" sin volver a preguntar por espacios: los tres use
 * cases normalizan, validan y guardan la misma cosa, que es la única forma de que la
 * validación describa lo que queda en la base.
 *
 * Devuelve el primer error encontrado o `null` si el cliente es válido.
 */
internal fun validarCliente(
    nombre: String,
    email: String?,
    telefono: String?,
): ErrorCliente? = when {
    nombre.isBlank() -> ErrorCliente.NombreVacio
    email != null && !FORMATO_EMAIL.matches(email) -> ErrorCliente.EmailInvalido
    telefono != null && !FORMATO_TELEFONO.matches(telefono) -> ErrorCliente.TelefonoInvalido
    else -> null
}

/**
 * Email, teléfono y notas son opcionales, y "opcional" en una pantalla significa
 * "vacío": el usuario deja el campo en blanco y no está informing nada.
 *
 * Se guarda `null` y no `""` porque el modelo los declara nullable para eso, y porque
 * una cadena vacía en la base se cuela por cualquier `isNotEmpty()` que alguien escriba
 * más adelante. Cortar los espacios de los bordes va incluido, porque `"  "` es tan
 * poco informado como `""`.
 */
internal fun normalizarTextoOpcional(valor: String?): String? = valor?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Formato de email **deliberadamente laxo**.
 *
 * No reimplementa RFC 5322 ni viene cerca: la RFC permite partes entre comillas,
 * literales de IP y comentarios, y un validador completo rechaza direcciones válidas
 * con la misma frecuencia con la que acepta basura. El objetivo real es agarrar los
 * errores de tipeo que se cuelan en la agenda — `juan@@mail.com`, `juan@mail`,
 * `juan mail.com` — y no diagnosticar entregas.
 *
 * Las tres reglas que sí importan: algo antes del arroba, un arroba, y un punto en el
 * dominio. Falla `juan@@mail.com` (dos arrobas) y `juan@mail` (sin punto), y acepta
 * `juan.perez+etiqueta@mail.com.ar`.
 */
private val FORMATO_EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/**
 * Teléfono: solo dígitos, espacios, `+`, `-`, `(` y `)`.
 *
 * **Deliberadamente no se valida por país.** Los formatos de numeración cambian y
 * cambian más: `+54 11 4321-1234` en Argentina, `600 12 34 56` en España,
 * `+1 (415) 555-0132` en Estados Unidos. Una lista de longitudes por prefijo rechaza
 * clientes legítimos de un día para otro, y la app es multi-país desde el README. Lo que
 * sí se rechaza son letras y símbolos, que es un error de tipeo con cualquier teclado
 * del mundo.
 */
private val FORMATO_TELEFONO = Regex("^[0-9+()\\-\\s]+$")
