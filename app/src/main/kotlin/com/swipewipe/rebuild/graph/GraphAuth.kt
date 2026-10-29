package com.swipewipe.rebuild.graph

object GraphAuth {

    const val AUTHORIZE_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
    const val TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
    const val REDIRECT_SCHEME = "swipewipe-rebuild"
    const val REDIRECT_HOST = "graph-auth"
    val SCOPES = "Files.ReadWrite.all User.Read offline_access"

    val pkce: Pair<String, String>
        get() {
            val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            val verifier = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            val digest = java.security.MessageDigest
                .getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            val challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
            return verifier to challenge
        }

    fun authorizeUrl(clientId: String, challenge: String): String =
        AUTHORIZE_URL +
            "?client_id=$clientId" +
            "&response_type=code" +
            "&redirect_uri=" + java.net.URLEncoder.encode("$REDIRECT_SCHEME://$REDIRECT_HOST", "UTF-8") +
            "&response_mode=query" +
            "&scope=" + java.net.URLEncoder.encode(SCOPES, "UTF-8") +
            "&prompt=select_account" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"

    data class TokenResponse(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSeconds: Long,
        val account: String
    )

    fun exchangeCode(clientId: String, code: String, verifier: String): TokenResponse {
        val client = okhttp3.OkHttpClient()
        val form = okhttp3.FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", "$REDIRECT_SCHEME://$REDIRECT_HOST")
            .add("scope", SCOPES)
            .add("code_verifier", verifier)
            .build()
        val resp = client.newCall(
            okhttp3.Request.Builder().url(TOKEN_URL).post(form).build()
        ).execute()
        resp.use { r ->
            val body = r.body?.string() ?: throw IllegalStateException("empty token response")
            val json = org.json.JSONObject(body)
            if (!r.isSuccessful) {
                throw IllegalStateException(json.optString("error_description", "token error"))
            }
            return fromJson(json)
        }
    }

    fun refreshToken(clientId: String, refresh: String): TokenResponse {
        val client = okhttp3.OkHttpClient()
        val form = okhttp3.FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("scope", SCOPES)
            .build()
        val resp = client.newCall(
            okhttp3.Request.Builder().url(TOKEN_URL).post(form).build()
        ).execute()
        resp.use { r ->
            val body = r.body?.string() ?: throw IllegalStateException("empty token response")
            val json = org.json.JSONObject(body)
            if (!r.isSuccessful) {
                throw IllegalStateException(json.optString("error_description", "token error"))
            }
            return fromJson(json)
        }
    }

    private fun fromJson(json: org.json.JSONObject): TokenResponse =
        TokenResponse(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token", ""),
            expiresInSeconds = json.optLong("expires_in", 3600),
            account = json.optJSONObject("id_token_claims")?.optString("preferred_username", "")
                ?: ""
        )
}
