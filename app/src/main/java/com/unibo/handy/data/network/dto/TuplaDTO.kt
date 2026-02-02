package com.unibo.handy.data.network.dto

import com.google.gson.annotations.SerializedName

data class TuplaDTO(
    // T1: ID del Richiedente
    @SerializedName("t1_requesterId") val t1RequesterId: String,
    // T2: ID del Target (chi possiede il service client)
    @SerializedName("t2_targetId") val t2TargetId: String,
    // T3: Beta+ X (Blur User + ServerSpecific + ServerGlobal)
    @SerializedName("t3_betaPlusX") val t3BetaPlusX: Long,
    // T4: Beta+ Y
    @SerializedName("t4_betaPlusY") val t4BetaPlusY: Long,
    // T5: Somma dei Blur Utenti (UserReq + UserTarget)
    // In chiaro per ora (senza Paillier)
    @SerializedName("t5_sumUserBlur") val t5SumUserBlur: Long,
    // T6: Somma dei Blur Server Specifici (SrvReq + SrvTarget)
    // In chiaro per ora
    @SerializedName("t6_sumServerBlur") val t6SumServerBlur: Long,
    // T7: Tolleranza (Raggio in metri o unità mappa)
    // In chiaro per ora
    @SerializedName("t7_tolerance") val t7Tolerance: Long
)
