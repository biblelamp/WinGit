#!/bin/sh
# WinGit launcher inside the Flatpak sandbox. @VERSION@ is replaced at build time;
# the app reads it like the version passed by its jpackage launcher.
exec /app/jre/bin/java -Djpackage.app-version=@VERSION@ -cp '/app/lib/wingit/*' cz.wingit.app.Main "$@"
