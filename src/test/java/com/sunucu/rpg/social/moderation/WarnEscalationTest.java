package com.sunucu.rpg.social.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WarnEscalationTest {

    @Test
    void ruleAppliesOnExactCount() {
        WarnEscalation escalation = WarnEscalation.fromConfig(List.of(
                Map.of("warnings", 3, "action", "mute", "duration", "1h"),
                Map.of("warnings", 5, "action", "ban")), message -> { });
        assertTrue(escalation.at(2).isEmpty());
        assertEquals(new WarnEscalation.Rule(3, Punishment.Type.MUTE, 3_600_000L), escalation.at(3).orElseThrow());
        assertTrue(escalation.at(4).isEmpty());
        assertEquals(0, escalation.at(5).orElseThrow().durationMillis(), "süre yoksa kalıcı");
    }

    @Test
    void badRulesAreSkippedWithWarning() {
        List<String> warnings = new ArrayList<>();
        WarnEscalation escalation = WarnEscalation.fromConfig(List.of(
                Map.of("warnings", 3, "action", "yak"),
                Map.of("warnings", "üç", "action", "mute"),
                Map.of("warnings", 2, "action", "mute", "duration", "yarın"),
                Map.of("warnings", 4, "action", "KICK", "duration", "1h")), warnings::add);
        assertEquals(3, warnings.size());
        assertEquals(0, escalation.at(4).orElseThrow().durationMillis(), "kick'te süre kullanılmaz");
    }

    @Test
    void emptyConfigIsOff() {
        assertTrue(WarnEscalation.fromConfig(List.of(), message -> { }).isEmpty());
    }
}
