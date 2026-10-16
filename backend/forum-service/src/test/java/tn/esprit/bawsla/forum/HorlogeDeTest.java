package tn.esprit.bawsla.forum;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Horloge réelle décalée d'une durée réglable : simule le passage des jours sans attendre. */
class HorlogeDeTest extends Clock {

    private final ZoneId zone = ZoneId.systemDefault();
    private volatile Duration decalage = Duration.ZERO;

    void avancer(Duration duree) {
        decalage = decalage.plus(duree);
    }

    void remettre() {
        decalage = Duration.ZERO;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(decalage);
    }
}
