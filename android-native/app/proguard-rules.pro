# Annotation metadata is necessary for DI and reflective Android framework integration.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
# Do not broadly disable obfuscation: annotate/keep only genuinely reflective entry points.
