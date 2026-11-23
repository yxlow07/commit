# Glance instantiates ActionCallback classes by name via reflection (Class.getDeclaredConstructor()),
# so R8 must keep their no-arg constructors. Without this, widget taps fail with NoSuchMethodException.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
