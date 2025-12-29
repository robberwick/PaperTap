package com.robberwick.papertap.waveshare

/**
 * Configuration for a specific WaveShare e-paper display model.
 *
 * @property width Display width in pixels
 * @property height Display height in pixels
 * @property commandByte Command byte identifier for this display type
 * @property packetCount Number of packets to send for image data
 * @property packetSize Size of each data packet in bytes
 * @property hasRedLayer Whether this display supports red color (dual-layer)
 * @property needsRotation Whether image needs 270° rotation before sending
 */
data class DisplayConfig(
    val width: Int,
    val height: Int,
    val commandByte: Byte,
    val packetCount: Int,
    val packetSize: Byte,
    val hasRedLayer: Boolean,
    val needsRotation: Boolean
) {
    companion object {
        /**
         * Display type configurations indexed by WaveShare SDK enum values (1-indexed).
         * Index 0 is unused to maintain compatibility with original SDK.
         *
         * Display types:
         * 1 = 2.13" (250x122)
         * 2 = 2.13" V2 (296x128)
         * 3 = 2.9" (400x300)
         * 4 = 4.2" (800x480)
         * 5 = 4.2" V2 (880x528)
         * 6 = 2.7" (264x176)
         * 7 = 2.13" B (296x128, red layer)
         * 8 = 1.54" B (200x200, red layer)
         */
        private val configs = arrayOf(
            // Index 0 - unused
            DisplayConfig(0, 0, 0, 0, 0, false, false),

            // Index 1 - 2.13" (250x122)
            DisplayConfig(250, 122, 19, 250, 19, false, true),

            // Index 2 - 2.13" V2 (296x128)
            DisplayConfig(296, 128, 19, 296, 19, false, true),

            // Index 3 - 2.9" (400x300)
            DisplayConfig(400, 300, 103, 150, 103, false, false),

            // Index 4 - 4.2" (800x480)
            DisplayConfig(800, 480, 123, 400, 123, false, false),

            // Index 5 - 4.2" V2 (880x528)
            DisplayConfig(880, 528, 123, 484, 123, false, false),

            // Index 6 - 2.7" (264x176)
            DisplayConfig(264, 176, 124, 48, 124, false, true),

            // Index 7 - 2.13" B with red (296x128)
            DisplayConfig(296, 128, 77, 64, 77, true, true),

            // Index 8 - 1.54" B with red (200x200)
            DisplayConfig(200, 200, 127, 50, 127, true, false)
        )

        /**
         * Get display configuration for a given display type.
         * @param displayType WaveShare display type (1-8)
         * @return Display configuration or null if invalid type
         */
        fun forDisplayType(displayType: Int): DisplayConfig? {
            return if (displayType in 1..8) configs[displayType] else null
        }

        /**
         * Validate if a bitmap matches the expected dimensions for a display type.
         * @param displayType WaveShare display type (1-8)
         * @param bitmapWidth Bitmap width
         * @param bitmapHeight Bitmap height
         * @return true if dimensions match (in either orientation)
         */
        fun validateDimensions(displayType: Int, bitmapWidth: Int, bitmapHeight: Int): Boolean {
            val config = forDisplayType(displayType) ?: return false
            return (bitmapWidth == config.width && bitmapHeight == config.height) ||
                   (bitmapWidth == config.height && bitmapHeight == config.width)
        }
    }
}
