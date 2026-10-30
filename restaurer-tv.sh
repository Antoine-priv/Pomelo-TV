#!/bin/bash
# Réactive les applis désactivées sur la TV et remet les animations par défaut.
# Usage : bash restaurer-tv.sh
TV=192.168.1.242:5555
adb connect "$TV"
# Remet l'écran d'accueil Google et retire l'accueil Pomelo TV
adb -s "$TV" shell pm enable com.google.android.tvlauncher
adb -s "$TV" uninstall com.pomelo.tv
for p in com.google.android.videos com.google.android.youtube.tvmusic com.google.android.play.games \
         com.google.android.feedback com.android.printspooler com.android.deskclock \
         com.vestel.vestelreporter fusion.android.tv.demo com.google.android.backdrop \
         com.google.android.apps.nbu.smartconnect.tv com.google.android.tvrecommendations \
         com.google.android.katniss com.vestel.tv.eib com.mediatek.tv.freeviewplay \
         com.google.android.tts com.google.android.syncadapters.calendar; do
  adb -s "$TV" shell pm enable "$p"
done
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb -s "$TV" shell settings put global "$k" 1.0
done
