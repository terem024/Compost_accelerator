package com.group11.compostsystem.dto;

import java.math.BigDecimal;

public record MoistureProbeDiagnostic(int probe, int gpio, Integer rawAdc,
                                      BigDecimal percent, boolean enabled, boolean valid) {
}
