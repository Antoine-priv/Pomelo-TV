package com.pomelo.tv;

import android.view.animation.Interpolator;

/**
 * Courbe d'un ressort, comme les animations d'Apple : la vitesse part de zéro et s'éteint progressivement,
 * sans arrêt net à l'arrivée. SOFT arrive sans dépasser ; LIVELY dépasse à peine avant de se poser.
 */
final class Spring implements Interpolator {

    static final Spring SOFT = new Spring(1f, 8.4f);
    static final Spring LIVELY = new Spring(0.78f, 8f);
    /** Comme SOFT, mais sa vitesse de pointe est divisée par quatre : l'animation dure plus longtemps (voir stretch). */
    static final Spring SOFT_SLOW_PEAK = new Spring(1f, 8.4f, 0.25f);

    private final float damping, omega, end;
    /** Partie du ressort où la vitesse dépasserait la limite, remplacée par une vitesse constante. */
    private final float capFrom, capTo, capSpeed, capDuration;
    /** Durée à multiplier pour que ce qui reste identique du ressort garde son rythme. */
    final float stretch;

    /** @param omega pulsation sur la durée de l'animation : assez haute pour que le ressort soit posé à la fin. */
    private Spring(float damping, float omega) {
        this(damping, omega, 1f);
    }

    private Spring(float damping, float omega, float peakRatio) {
        this.damping = damping;
        this.omega = omega;
        end = raw(1f);
        if (peakRatio >= 1f) {
            capFrom = capTo = capSpeed = capDuration = 0;
            stretch = 1f;
            return;
        }
        float peakAt = peakTime();
        capSpeed = speed(peakAt) * peakRatio;
        capFrom = crossing(0f, peakAt, true);
        capTo = crossing(peakAt, 1f, false);
        capDuration = (raw(capTo) - raw(capFrom)) / end / capSpeed;
        stretch = 1f - (capTo - capFrom) + capDuration;
    }

    private float speed(float t) {
        final float h = 0.0005f;
        return (raw(t + h) - raw(t - h)) / (2 * h) / end;
    }

    private float peakTime() {
        float best = 0f;
        for (float t = 0f; t < 1f; t += 0.001f) if (speed(t) > speed(best)) best = t;
        return best;
    }

    /** Instant où la vitesse atteint la limite, en montant ou en descendant. */
    private float crossing(float from, float to, boolean rising) {
        for (int i = 0; i < 30; i++) {
            float mid = (from + to) / 2;
            if ((speed(mid) < capSpeed) == rising) from = mid;
            else to = mid;
        }
        return (from + to) / 2;
    }

    private float raw(float t) {
        double decay = Math.exp(-damping * omega * t);
        if (damping >= 1f) return (float) (1 - (1 + omega * t) * decay);
        double wd = omega * Math.sqrt(1 - damping * damping);
        return (float) (1 - decay * (Math.cos(wd * t) + damping * omega / wd * Math.sin(wd * t)));
    }

    @Override
    public float getInterpolation(float t) {
        if (stretch == 1f) return raw(t) / end;
        float time = t * stretch;
        if (time <= capFrom) return raw(time) / end;
        if (time <= capFrom + capDuration) return raw(capFrom) / end + capSpeed * (time - capFrom);
        return raw(Math.min(1f, capTo + time - capFrom - capDuration)) / end;
    }
}
