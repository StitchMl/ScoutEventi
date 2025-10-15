############################################
# Hilt (regole minimali e mirate)
############################################

# Classi generate che Hilt risolve via riflessione
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
-keep class dagger.hilt.internal.processedrootsentinel.codegen.** { *; }
-keep class hilt_aggregated_deps.** { *; }

# Evita warning per annotazioni javax (comune con Dagger/Hilt)
-dontwarn javax.annotation.**

############################################
# Jsoup
############################################
# Nessuna regola necessaria: Jsoup non usa riflessione per le API che usiamo.
# (R8 terrà automaticamente le classi effettivamente referenziate.)
# -> Rimuovi eventuali:
# -keep class org.jsoup.** { *; }

############################################
# (Opzionali) Regole generiche comuni
############################################
# Mantieni il costruttore dell'Application (non strettamente necessario ma innocuo)
-keep class it.buonacaccia.app.App { <init>(); }