package com.phonecam

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.SessionDescription

class Signaling(private val client: OkHttpClient = OkHttpClient()) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun exchangeOffer(url: String, offer: SessionDescription): SessionDescription = withContext(Dispatchers.IO) {
        val payload = JSONObject()
        payload.put("sdp", offer.description)
        payload.put("type", offer.type.canonicalForm())

        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Bad response ${'$'}{resp.code}")
            val body = resp.body?.string() ?: throw IllegalStateException("Empty body")
            val json = JSONObject(body)
            val answerSdp = json.getString("sdp")
            val answerType = json.optString("type", "answer")
            return@use SessionDescription(SessionDescription.Type.fromCanonicalForm(answerType), answerSdp)
        }
    }
}
