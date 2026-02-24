package com.unibo.handy.data.network.dto

import com.google.gson.annotations.SerializedName

data class TuplaDTO(
    // T1: ID del Richiedente
    @SerializedName("t1_requesterId") val t1RequesterId: String,
    // T2: ID del Target (chi possiede il service client)
    @SerializedName("t2_targetId") val t2TargetId: String,
    // T3: Beta+ X (Blur User + ServerSpecific + ServerGlobal)
    @SerializedName("t3_betaPlusX") val t3BetaPlusX: Long,
    // T3: Beta+ Y
    @SerializedName("t4_betaPlusY") val t3BetaPlusY: Long,
    // T4: Somma dei Blur Utenti (UserReq + UserTarget)
    @SerializedName("t5_sumUserBlur") val t4SumUserBlur: String,
    // T5: Somma dei Blur Server Specifici (SrvReq + SrvTarget)
    @SerializedName("t6_sumServerBlur") val t5SumServerBlur: Long,
    // T6: Tolleranza (Raggio in metri o unità mappa)
    @SerializedName("t7_tolerance") val t6Tolerance: String
)
