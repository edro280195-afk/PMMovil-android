# ============================================================================
#  Reglas de R8 para PMMovil
#
#  R8 borra y renombra todo lo que no ve usado. Lo que NO puede ver son los usos
#  por reflexión: Gson construyendo un modelo desde JSON, Retrofit leyendo los
#  tipos genéricos de una interfaz, o el código nativo llamando de vuelta a Java.
#  Cada bloque de aquí abajo dice qué se salva y por qué.
#
#  Estas fallas NO aparecen al compilar: aparecen en ejecución, y normalmente
#  como un campo en null o una lista vacía en la pantalla que menos se prueba.
#  Si algo deja de funcionar sólo en release, empezar por aquí.
# ============================================================================

# ── Trazas legibles ─────────────────────────────────────────────────────────
# Sin esto, un crash en producción llega con nombres tipo a.b.c y números de
# línea inventados. Sentry ya está en el proyecto; que reporte algo útil.
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*,RuntimeVisibleAnnotations,AnnotationDefault
-renamesourcefileattribute SourceFile


# ── Modelos de datos (Gson) ─────────────────────────────────────────────────
# Gson crea estas clases por reflexión y llena sus campos por nombre. R8 no ve
# ningún constructor llamado ni ningún campo escrito, así que sin esta regla los
# borra o los renombra y todas las respuestas del API llegan vacías.
# @SerializedName protege el nombre en el JSON, no el campo en el bytecode.
-keep class com.example.pmmovil.data.model.** { *; }
-keepclassmembers class com.example.pmmovil.data.model.** {
    <init>(...);
    <fields>;
}

# Los enums que viajan en JSON se resuelven por su nombre.
-keepclassmembers enum com.example.pmmovil.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Gson: el genérico de TypeToken se lee en ejecución.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}


# ── Retrofit ────────────────────────────────────────────────────────────────
# La interfaz del API se implementa en ejecución con un Proxy dinámico, y el
# tipo de retorno (Response<LoginResponse>) se lee de la firma genérica.
-keep,allowobfuscation interface com.example.pmmovil.data.api.**
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>


# ── Room ────────────────────────────────────────────────────────────────────
# Las entidades y DAOs se resuelven por nombre generado.
-keep class com.example.pmmovil.data.db.** { *; }


# ── SignalR ─────────────────────────────────────────────────────────────────
# El hub deserializa los argumentos de cada mensaje por reflexión contra el
# tipo declarado en el `on(...)`.
-keep class com.microsoft.signalr.** { *; }
-dontwarn com.microsoft.signalr.**


# ── Código nativo (JNI) ─────────────────────────────────────────────────────
# Todas éstas llaman de Java a C++ y de vuelta. Un renombre las rompe con un
# UnsatisfiedLinkError en el primer uso.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
-keep class com.paddle.ocr.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# AhmerPdfium carga el motor nativo por reflexión.
-keep class com.ahmer.pdfium.** { *; }
-keep class com.ahmer.pdfviewer.** { *; }

# PDFBox: recursos y fuentes por nombre.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**


# ── Impresión Zebra ─────────────────────────────────────────────────────────
# Los layouts de etiqueta se guardan como JSON en preferencias y se releen con
# Gson: mismo caso que los modelos del API.
-keep class com.example.pmmovil.data.printing.** { *; }
-keep class com.example.pmmovil.data.preferences.** { *; }


# ── Kotlin ──────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-dontwarn kotlinx.**


# ── Ruido de dependencias ───────────────────────────────────────────────────
# Referencias a APIs de escritorio o a clases opcionales que en Android no
# existen. No son errores: son ramas que nunca se toman.
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn org.slf4j.**
