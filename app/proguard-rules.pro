# Room / Hilt / Compose aportan sus propias reglas de keep.
# Solo hace falta preservar clases que se resuelven por reflexio en runtime.

# Modelos de dominio usados por kotlinx.serialization (backup/export futuro).
-keepclassmembers class com.turnero.app.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.turnero.app.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
