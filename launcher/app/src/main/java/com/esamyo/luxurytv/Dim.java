/*
 * Luxury TV
 * Copyright (c) 2026 esamyo (Esam). MIT License, see LICENSE and NOTICE.md.
 * This notice must stay in every copy and modified version.
 */
package com.esamyo.luxurytv;

/** The resting dim level of the Wi-Fi and Settings icons; the header's clock, greeting and date use the same grey. */
final class Dim {
    private Dim() {
    }

    /** 60 %: the icons' rest; the focused one comes up to full brightness. */
    static final float REST = 0.6f;
    /** White at REST on black, as a solid colour (solid so joined Arabic letters draw without seams). */
    static final int HEADER_TEXT = 0xFF999999;
}
