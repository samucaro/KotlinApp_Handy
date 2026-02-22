package com.unibo.handy.data.network

import com.unibo.handy.data.network.dto.FcmTokenDTO
import com.unibo.handy.data.network.dto.HeartBeatDTO
import com.unibo.handy.data.network.dto.HelpRequestDTO
import com.unibo.handy.data.network.dto.RegistrationDTO
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.Response


interface ServiceAPI {
    @POST("/register_profile")
    suspend fun registerProfile(@Body payload: RegistrationDTO): Response<Unit>

    @POST("/heartbeat")
    suspend fun sendHeartbeat(@Body payload: HeartBeatDTO): Response<Unit>

    @POST("/help_request")
    suspend fun sendHelpRequest(@Body payload: HelpRequestDTO): Response<Unit>

    @POST("/update_fcm_token")
    suspend fun updateFcmToken(@Body payload: FcmTokenDTO): Response<Unit>
}