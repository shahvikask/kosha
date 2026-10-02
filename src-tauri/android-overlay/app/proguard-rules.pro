# Keep the Play / Keystore plugin. R8 would strip @Command methods otherwise.
-keep class in.growurdhan.app.GrowUrDhanPlugin { *; }
-keep class in.growurdhan.app.GrowUrDhanPlugin$* { *; }
-keep class in.growurdhan.app.GmailClient { *; }
-keep class app.tauri.** { *; }
-keepclassmembers class * {
    @app.tauri.annotation.Command <methods>;
    @app.tauri.annotation.InvokeArg <fields>;
}
-keepattributes *Annotation*
-dontwarn com.google.android.play.core.integrity.**
-dontwarn com.google.android.play.core.review.**
