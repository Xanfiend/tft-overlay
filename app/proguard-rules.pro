# TFT Scryer — R8/ProGuard rules
#
# Goal: obfuscate our own classes (strip names so the APK can't be trivially
# reverse-engineered) while keeping the bits that break under shrinking.
# Manifest-declared components (MainActivity, OverlayService, the services,
# TFTAccessibilityService) are kept automatically by R8 from the manifest —
# no rules needed for those.

# --- ML Kit text recognition (bundled, offline) ---
# ML Kit loads model/recognizer classes via reflection at runtime; shrinking
# or renaming them breaks OCR. Keep the whole package and its annotations.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-dontwarn com.google.mlkit.**

# --- AndroidX ---
-keep class androidx.appcompat.** { *; }
-dontwarn androidx.**

# Keep line numbers for readable crash traces, but hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
