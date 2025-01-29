package ai.enginery.module

import com.badlogic.gdx.Gdx
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

object Requests {

    fun getSignedUrl(agentId: String, apiKey: String): Map<String, Any>? {
        // Vytvoření klienta OkHttp
        val client = OkHttpClient()

        // Vytvoření požadavku
        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/convai/conversation/get_signed_url?agent_id=$agentId")
            .addHeader("xi-api-key", apiKey) // Přidání hlavičky pro API key
            .build()

        // Odeslání požadavku a zpracování odpovědi
        return try {
            val response: Response = client.newCall(request).execute()

            // Získání těla odpovědi a její deserializace do Map
            response.body?.use { body ->
                if (response.isSuccessful) {

                    if (response.code == 200) {
                        // Deserializace JSON odpovědi do Map<String, Any>
                        val json = body.byteStream().reader().readText()
                        val gson = Gson()
                        val resultMap: Map<String, Any> = gson.fromJson(json, Map::class.java) as Map<String, Any>
                        Gdx.app.log("Request", "Body " + json)
                        return  resultMap
                    }
                    return null
                } else {
                    // Pokud je odpověď neúspěšná, vrátíme null
                    Gdx.app.error(
                        "Request",
                        "Request failed with status code: ${response.code}, body: ${body.string()}"
                    )
                    return null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
