# ProGuard / R8 rules for the release build of the on-device agent.
#
# Most modern AndroidX / Kotlin / Compose libraries ship consumer ProGuard
# rules in their AAR (R8 reads them automatically), so the rules below only
# cover what is *not* covered by a library's own `consumer-rules.pro`:
#  - reflection-driven code paths (Koog agents, kotlinx.serialization).
#  - native interop layers that R8 has no AST visibility into
#    (MediaPipe / LiteRT / SQLCipher).
#  - AppFunctions KSP-generated wrappers that the platform calls via
#    reflection at install time.
#  - Stack-trace fidelity for Crashlytics.

# ─── Stack traces ────────────────────────────────────────────────────────────
# Preserve file + line info so Crashlytics-mapped stacks resolve to the right
# source positions. `-renamesourcefileattribute` makes obfuscated source-file
# attribute report a stable name instead of the original path.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── Kotlin metadata + annotations ───────────────────────────────────────────
# Required by every reflection-using library (Koog, kotlinx.serialization,
# Hilt's Kotlin codegen). The Kotlin Gradle plugin no longer adds these
# implicitly when consuming third-party AARs.
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# ─── Coroutines ──────────────────────────────────────────────────────────────
# The `coroutines-core` AAR ships its own rules; this only keeps the
# debug-only ServiceLoader entry so R8 doesn't warn on missing classes.
-dontwarn kotlinx.coroutines.debug.**

# ─── kotlinx.serialization (used transitively by Koog) ───────────────────────
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# ─── Gson ────────────────────────────────────────────────────────────────────
# `app_functions_*.xml` and chat-export payloads round-trip through Gson.
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-dontwarn com.google.gson.**

# ─── MediaPipe + LiteRT (native + reflection) ────────────────────────────────
# JNI bindings reach into Java classes by name; R8 cannot follow native frame.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.ai.edge.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.ai.edge.**
-dontwarn org.tensorflow.lite.**

# ─── SQLCipher ───────────────────────────────────────────────────────────────
# `net.zetetic:sqlcipher-android` loads its native lib by reflection.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# ─── Koog (reflection-heavy agent framework) ─────────────────────────────────
# Koog uses kotlinx.serialization + reflection to materialise nodes, tools,
# and pipeline graphs at runtime. Keep the whole surface — shrinking gains
# from minifying Koog are small relative to the runtime breakage risk.
-keep class ai.koog.** { *; }
-keepclassmembers class ai.koog.** { *; }
-dontwarn ai.koog.**

# ─── Ktor (used by Koog HTTP clients) ────────────────────────────────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ─── AppFunctions (KSP-generated callee + caller wrappers) ───────────────────
# `androidx.appfunctions` discovers `*_AppFunctionInventory` and
# `*_AppFunctionInvoker` classes by reflection at runtime; any `@AppFunction`-
# annotated method is invoked through the generated invoker. Stripping or
# renaming either side breaks the system AppFunctions dispatch path.
-keep class * implements androidx.appfunctions.AppFunctionInventory { *; }
-keep class * implements androidx.appfunctions.AppFunctionInvoker { *; }
-keep @androidx.appfunctions.AppFunction class *
-keepclassmembers class * {
    @androidx.appfunctions.AppFunction <methods>;
}
-keep class androidx.appfunctions.** { *; }
-dontwarn androidx.appfunctions.**

# ─── Hilt ────────────────────────────────────────────────────────────────────
# AGP's Hilt plugin ships most rules, but the `_HiltModules` aggregated
# components occasionally get over-shrunk on R8 full-mode. Pin them.
-keep class * extends dagger.hilt.android.internal.managers.* { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**

# ─── OpenTelemetry + AutoValue (transitive, optional symbols) ───────────────
# `io.opentelemetry-api-incubator` and the `auto-value` annotation are
# compile-time-only optional dependencies referenced by OpenTelemetry SDK
# internals reachable through Koog. R8 only needs to know it can safely
# omit warnings — the runtime path that would use them is never executed
# because the incubator module is not on the runtime classpath.
-dontwarn com.google.auto.value.AutoValue$CopyAnnotations
-dontwarn io.opentelemetry.api.incubator.**

# ─── Room ────────────────────────────────────────────────────────────────────
# Room's annotation processor generates `*_Impl` classes that subclass our
# DAOs and the database; consumer rules cover this, but we keep an explicit
# blanket rule for safety since the DB instantiation is reflective.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**
