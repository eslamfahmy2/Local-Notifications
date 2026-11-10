package com.example.localnotifications.core.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET

/**
 * The feed is XML, so the raw body is returned and handed to a NotificationParser.
 * Keeping Retrofit free of converters means the parsing strategy stays swappable.
 */
interface NotificationApi {

    @GET("localalerts.php")
    suspend fun getNotifications(): ResponseBody
}
