# Reflection-based libraries require generic signatures and runtime annotations.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault

# Room, Hilt and Media3 publish consumer rules. App-specific model rules are added with
# the corresponding models in later waves to avoid retaining unrelated code.
