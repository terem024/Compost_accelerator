package com.group11.compostsystem.service;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;

final class ActuatorQuietHours {
    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    private static final LocalTime START = LocalTime.of(18, 0);
    private static final LocalTime END = LocalTime.of(8, 31);

    private ActuatorQuietHours() {}

    static boolean isQuiet(Clock clock) {
        LocalTime time = LocalTime.now(clock.withZone(ZONE));
        return !time.isBefore(START) || time.isBefore(END);
    }
}
