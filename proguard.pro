# Only shrink, don't do anything else
-dontobfuscate
-dontoptimize

-dontnote **

# Java Module System
-keep class module-info
-keepclassmembers class module-info { *; }
-keepattributes Module*

# Preserve all annotations.
-keepattribute *Annotation*

-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod

# Preserve all public applications.
-keepclasseswithmembers public class * {
	public static void main(java.lang.String[]);
}

-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.satergo.** { *; }
-keep class com.sun.** { *; }
-keep class javafx.** { *; }
-keep class jfxtras.styles.jmetro.** { *; }
-keep class impl.jfxtras.styles.jmetro.** { *; }
-keep class org.ergoplatform.restapi.** { *; }
-keep class org.ergoplatform.appkit.** { *; }
-keep class scorex.util.encode.** { *; }
-keep class sigmastate.** { *; }
-keep class sigma.** { *; }
-keep class scalan.** { *; }
-keep class fastparse.** { *; }
-keep class scala.util.control.** { *; }
-keep class scala.collection.** { *; }
-keep class scala.package** { *; }
-keep class special.** { *; }
-keep class com.google.zxing.** { *; }
-keep class wrappers.**  { *; }

-dontwarn scala.concurrent.**
-dontwarn scala.reflect.**
-dontwarn scala.collection.immutable.VM
-dontwarn scala.tools.**
-dontwarn **$$anonfun$*
-dontwarn algebra.**,spire.**,debox.**

-dontwarn org.controlsfx.**
-dontwarn org.fusesource.jansi.**,jtermios.windows.**
-dontwarn retrofit2.**,okhttp3.**

# Preserve all native method names and the name of their classes.
-keepclasseswithmembernames class * {
	native <methods>;
}

# Preserve the special static methods that are required in all enumeration classes.
-keepclassmembers,allowoptimization enum * {
	public static **[] values();
	public static ** valueOf(java.lang.String);
}

# Explicitly preserve all serialization members. The Serializable interface
# is only a marker interface, so it wouldn't save them.
-keepclassmembers class * implements java.io.Serializable {
	static final long serialVersionUID;
	private static final java.io.ObjectStreamField[] serialPersistentFields;
	private void writeObject(java.io.ObjectOutputStream);
	private void readObject(java.io.ObjectInputStream);
	java.lang.Object writeReplace();
	java.lang.Object readResolve();
}