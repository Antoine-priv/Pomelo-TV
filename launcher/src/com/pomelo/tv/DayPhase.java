package com.pomelo.tv;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Lumière du moment en France : les deux variantes de fond qui encadrent l'heure actuelle et la proportion
 * de la seconde. Chaque variante est calée sur la hauteur réelle du soleil (formules NOAA), ce qui suit à la fois
 * l'heure française, le changement d'heure et la longueur du jour selon la date.
 *
 * Ce que montrent les images :
 *   night      lune et étoiles, nuit noire ;
 *   bluedawn   ciel bleu froid sans soleil, aube civile ;
 *   sunrise    soleil posé sur l'horizon, tons roses ;
 *   morning    soleil à mi-hauteur, lumière claire ;
 *   noon       soleil au plus haut ;
 *   afternoon  soleil à mi-hauteur, lumière dorée ;
 *   sunset     soleil sur l'horizon, tons rouges ;
 *   bluedusk   ciel violet sans soleil, crépuscule.
 */
final class DayPhase {

    /** Le fuseau de la télé peut être un simple décalage sans changement d'heure : on impose celui de la France. */
    static final TimeZone FRANCE = TimeZone.getTimeZone("Europe/Paris");
    private static final double LATITUDE = 48.85, LONGITUDE = 2.35; // Paris

    /** Les variantes ont leur soleil à des places différentes : un fondu court évite d'en voir deux longtemps. */
    private static final double MAX_SUN_FADE_MINUTES = 30;

    final String from, to;
    final float mix;

    private DayPhase(String from, String to, float mix) {
        this.from = from;
        this.to = to;
        this.mix = mix;
    }

    static DayPhase now() {
        return at(Calendar.getInstance(FRANCE));
    }

    /** Heure française de la date donnée, en tenant compte du changement d'heure de ce jour-là. */
    static DayPhase at(Calendar c) {
        double m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) + c.get(Calendar.SECOND) / 60.0;
        Sun sun = new Sun(c);
        double midHeight = Math.min(14, sun.maxElevation * 0.55);
        double morning = sun.morningAt(midHeight), afternoon = sun.eveningAt(midHeight);
        double noonHalf = Math.max(45, (sun.eveningAt(-0.83) - sun.morningAt(-0.83)) * 0.1);

        // Instant et variante à cet instant : entre deux variantes identiques, l'image reste fixe.
        double[] at = {
                sun.morningAt(-12), sun.morningAt(-6), sun.morningAt(-3), sun.morningAt(-0.83), sun.morningAt(3),
                morning - MAX_SUN_FADE_MINUTES, morning, sun.noon - noonHalf - MAX_SUN_FADE_MINUTES, sun.noon - noonHalf,
                sun.noon + noonHalf, sun.noon + noonHalf + MAX_SUN_FADE_MINUTES, afternoon, sun.eveningAt(5),
                sun.eveningAt(1.5), sun.eveningAt(-2), sun.eveningAt(-5), sun.eveningAt(-8), sun.eveningAt(-12),
        };
        String[] names = {
                "night", "bluedawn", "bluedawn", "sunrise", "sunrise",
                "sunrise", "morning", "morning", "noon",
                "noon", "afternoon", "afternoon", "afternoon",
                "sunset", "sunset", "bluedusk", "bluedusk", "night",
        };
        // Les jours courts, certains instants se croisent : on garde l'ordre pour que la suite reste croissante.
        for (int i = 1; i < at.length; i++) at[i] = Math.max(at[i], at[i - 1]);
        if (m < at[0] || m >= at[at.length - 1]) return new DayPhase("night", "night", 0);
        for (int i = 0; i < at.length - 1; i++) {
            if (m >= at[i] && m < at[i + 1]) {
                float t = (float) ((m - at[i]) / (at[i + 1] - at[i]));
                return new DayPhase(names[i], names[i + 1], names[i].equals(names[i + 1]) ? 0 : t);
            }
        }
        return new DayPhase("night", "night", 0);
    }

    /** Position du soleil pour une date, en minutes depuis minuit, heure française. */
    private static final class Sun {
        final double noon, maxElevation;
        private final double declination, latitude = Math.toRadians(LATITUDE);

        Sun(Calendar c) {
            double g = 2 * Math.PI / 365 * (c.get(Calendar.DAY_OF_YEAR) - 1);
            double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                    - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
            declination = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g)
                    - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g)
                    - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
            double offset = FRANCE.getOffset(c.getTimeInMillis()) / 60000.0;
            noon = 720 - 4 * LONGITUDE - eqTime + offset;
            maxElevation = 90 - Math.abs(LATITUDE - Math.toDegrees(declination));
        }

        /** Écart en minutes entre midi solaire et le moment où le soleil est à cette hauteur. */
        private double minutesFromNoon(double elevation) {
            double cos = (Math.sin(Math.toRadians(elevation)) - Math.sin(latitude) * Math.sin(declination))
                    / (Math.cos(latitude) * Math.cos(declination));
            return 4 * Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
        }

        double morningAt(double elevation) {
            return noon - minutesFromNoon(elevation);
        }

        double eveningAt(double elevation) {
            return noon + minutesFromNoon(elevation);
        }
    }
}
