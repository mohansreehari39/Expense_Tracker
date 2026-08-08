# Kharcha release-build R8 rules. This app's stack (Ktor client +
# kotlinx.serialization, Room via KSP, ZXing) mostly ships its own
# consumer proguard rules, but kotlinx.serialization's generated
# `$serializer` companions are looked up by class name/reflection and can
# get renamed or stripped without these explicit keeps — that would
# silently break every request/response DTO in Dto.kt.

# kotlinx.serialization: keep generated serializers for every @Serializable
# class in this app's own packages, and the annotations R8 needs to see
# to find them.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclasseswithmembers class et.android.kharcha.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class et.android.kharcha.**$$serializer { *; }
-keepclassmembers class et.android.kharcha.** {
    *** Companion;
}
-keepclasseswithmembers class et.android.kharcha.** {
    <fields>;
}

# Ktor pulls in engine implementations reflectively; only the Android
# engine is actually on the classpath here, so quiet the rest instead of
# failing the build over classes that were never going to be present.
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# Room's generated DAO implementations reference entities directly, not
# reflectively, but keep entity/DTO field names anyway since the
# generated SQL binds by reflection-derived column order in a few paths.
-keep class et.android.kharcha.data.local.** { *; }
