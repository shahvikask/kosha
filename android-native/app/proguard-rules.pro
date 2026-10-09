# Annotation metadata is necessary for DI and reflective Android framework integration.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
# Do not broadly disable obfuscation: annotate/keep only genuinely reflective entry points.

# The validated native PDF bridge calls Chaquopy reflectively on ARM64 release.
# Keep runtime API entry points while retaining app/domain obfuscation elsewhere.
-keep class com.chaquo.python.** { *; }
