# Room generated implementations are looked up reflectively by Room_Impl.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# kotlinx.serialization keeps its generated serializers on the companion/`$serializer`.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.dualshield.phone.** {
    *** Companion;
}
-keepclasseswithmembers class com.dualshield.phone.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dualshield.phone.**$$serializer { *; }

# Telecom / system entry points are instantiated by the framework by name.
-keep class com.dualshield.phone.telecom.** { *; }
-keep class com.dualshield.phone.sms.** { *; }
