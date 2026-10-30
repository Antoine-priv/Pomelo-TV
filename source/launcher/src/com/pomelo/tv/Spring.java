package com.pomelo.tv;

import android.view.animation.Interpolator;

/**
 * Courbe d'un ressort, comme les animations d'Apple : la vitesse part de zéro et s'éteint progressivement,
 * sans arrêt net à l'arrivée. SOFT arrive sans dépasser ; LIVELY dépasse à peine avant de se poser.
 */
final class Spring implements Interpolator {

    static final Spring SOFT = new Spring(1f, 8.4f);
    static final Spring LIVELY = new Spring(0.78f, 8f);

    private final float damping, omega, end;

    /** @param omega pulsation sur la durée de l'animation : assez haute pour que le ressort soit posé à la fin. */
    private Spring(float damping, float omega) {
        this.damping = damping;
        this.omega = omega;
        end = raw(1f);
    }

    private float raw(float t) {
        double decay = Math.exp(-damping * omega * t);
        if (damping >= 1f) return (float) (1 - (1 + omega * t) * decay);
        double wd = omega * Math.sqrt(1 - damping * damping);
        return (float) (1 - decay * (Math.cos(wd * t) + damping * omega / wd * Math.sin(wd * t)));
    }

    @Override
    public float getInterpolation(float t) {
        return raw(t) / end;
    }
}
