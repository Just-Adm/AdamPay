# Hilt, Room, Compose and kotlinx.serialization ship their own consumer rules.

# Keep @Serializable classes' generated serializers (backups, saved layouts).
-keepclassmembers @kotlinx.serialization.Serializable class com.adampay.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes *Annotation*, InnerClasses, Signature

# Type-safe navigation looks routes up by class at runtime; keep them intact.
-keep class com.adampay.app.ui.Dest { *; }
-keep class com.adampay.app.ui.Dest$* { *; }

# Glance creates widget ActionCallbacks by class name (one-tap quick entries on the widget).
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
