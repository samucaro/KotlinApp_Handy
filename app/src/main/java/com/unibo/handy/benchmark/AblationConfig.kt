package com.unibo.handy.benchmark

object AblationConfig {
    enum class Mode {
        FULL_PROTOCOL,       // Blur + Paillier (SamaritanCloud puro)
        ONLY_PAILLIER,       // Coordinate esatte (niente blur), ma distanze calcolate omomorficamente
        ONLY_BLUR,           // Coordinate offuscate (Blur), ma tolleranza/distanza in chiaro
        BASELINE_PLAINTEXT   // Niente Blur, niente Paillier (Coordinate GPS pure e calcolo distanza in chiaro)
    }

    val CURRENT_MODE = Mode.FULL_PROTOCOL
}