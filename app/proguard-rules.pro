############################################
# Jsoup
############################################
# Jsoup supporta RE2/J come backend opzionale. L'app usa il backend Java standard,
# quindi le classi opzionali possono essere ignorate in sicurezza da R8.
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

############################################
# Regole dell'app
############################################
# Mantiene il costruttore dell'Application usato dal framework Android.
-keep class it.buonacaccia.app.App { <init>(); }
