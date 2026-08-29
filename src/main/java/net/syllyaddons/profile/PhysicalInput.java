package net.syllyaddons.profile;

import java.util.Objects;

public record PhysicalInput(InputDevice device, int code) {
    public PhysicalInput {
        Objects.requireNonNull(device, "device");
        if (code < 0) throw new IllegalArgumentException("Input code must not be negative");
    }
}
