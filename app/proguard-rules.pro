# Rules prepared for a future R8 release build. R8 remains disabled for now.
# Firestore instantiates these DTOs through reflection.
-keep class com.ronaldcolocho.taskly.data.model.** { *; }

# Gson deserializes the YouTube API response models reflectively.
-keep class com.ronaldcolocho.taskly.data.remote.youtube.** { *; }

# Preserve the existing domain model rule until a minified release has been
# validated end-to-end with Firebase, media playback, and attachment flows.
-keep class com.ronaldcolocho.taskly.domain.model.** { *; }
