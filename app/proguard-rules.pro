# Keep line numbers so release crash stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Media3, Compose, Haze and Backdrop ship their own consumer rules.
# Add narrow keeps here only if a release-only crash proves one is needed.
