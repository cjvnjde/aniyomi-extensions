package eu.kanade.tachiyomi.animeextension.ar.animerco.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.POST
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient

class FembedExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String): List<Video> {
        val videoApi = url.replace("/v/", "/api/source/").replace("https://www.fembed.com", "https://suzihaza.com")
        // val jsonR = Json.decodeFromString<JSONObject>(client.newCall(POST(videoApi)).execute().body!!.string())
        /*val jsonR = Json.decodeFromString<JsonObject>(
            Jsoup.connect(videoApi).ignoreContentType(true)
                .execute().body()
        )*/
        val jsonR = Json.decodeFromString<JsonObject>(
            client.newCall(POST(videoApi)).execute().body!!.string()
        )
        val jsonText = client.newCall(POST(videoApi)).execute().body!!.string()

        val videoList = mutableListOf<Video>()
        if (jsonR["success"].toString() == "true") {
            val videoList = mutableListOf<Video>()
            jsonR["data"]!!.jsonArray.forEach() {
                val videoUrl = it.jsonObject["file"].toString().trim('"')
                val quality = "Fembed:" + it.jsonObject["label"].toString().trim('"')
                videoList.add(Video(videoUrl, quality, videoUrl))
            }
            /*val jsonArray = json.getJSONArray("data")
            Log.i("jsoon", "$jsonArray")
            for (i in 0 until jsonArray.length()) {
                val `object` = jsonArray.getJSONObject(i)
                val videoUrl = `object`.getString("file")
                val quality = "Fembed:" + `object`.getString("label")
                videoList.add(Video(videoUrl, quality, videoUrl))
            }*/
            return videoList
        } else {
            val videoUrl = "not used"
            val quality = "Video taken down for dmca"
            videoList.add(Video(videoUrl, quality, videoUrl))
        }
        return videoList
    }
}
