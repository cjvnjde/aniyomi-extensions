package eu.kanade.tachiyomi.animeextension.en.genoanime

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.en.genoanime.extractors.FembedExtractor
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.doodextractor.DoodExtractor
import eu.kanade.tachiyomi.lib.streamsbextractor.StreamSBExtractor
import eu.kanade.tachiyomi.lib.streamtapeextractor.StreamTapeExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.Exception

class GenoAnime : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Genoanime"
    override val baseUrl = "https://www.genoanime.com"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // Popular Anime
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/browse?sort=top_rated&page=$page")

    override fun popularAnimeSelector(): String = "div.trending__product div.col-lg-10 div.row div.col-lg-3.col-6"
    override fun popularAnimeNextPageSelector(): String = "div.text-center a i.fa.fa-angle-double-right"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain("$baseUrl/${element.select("div.product__item a").attr("href").removePrefix("./")}")
        anime.title = element.select("div.product__item__text h5 a:nth-of-type(2)").first().text()
        anime.thumbnail_url = "$baseUrl/${element.select("div.product__item__pic").attr("data-setbg").removePrefix("./")}"
        return anime
    }

    // Latest Anime
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/browse?sort=latest&page=$page", headers)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    // Search Anime
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val formBody = FormBody.Builder()
            .add("anime", query)
            .build()
        val newHeaders = headersBuilder()
            .set("Content-Length", formBody.contentLength().toString())
            .set("Content-Type", formBody.contentType().toString())
            .build()
        return POST("$baseUrl/data/searchdata.php", newHeaders, formBody)
    }

    override fun searchAnimeSelector(): String = "div.col-lg-3"
    override fun searchAnimeNextPageSelector(): String = "div.text-center.product__pagination a.search-page i.fa.fa-angle-double-left"

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain("$baseUrl/${element.select("a").attr("href").removePrefix("./")}")
        anime.title = element.select("div.product__item__text h5 a:nth-of-type(2)").text()
        anime.thumbnail_url = "$baseUrl/${element.select("div.product__item div.product__item__pic.set-bg").attr("data-setbg").removePrefix("./")}"
        return anime
    }

    // Episode

    override fun episodeListParse(response: Response): List<SEpisode> {
        return super.episodeListParse(response).reversed()
    }

    override fun episodeListSelector() = "div.anime__details__episodes div.tab-pane a"

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        episode.setUrlWithoutDomain(element.attr("href"))
        episode.name = element.select("a").text()
        episode.episode_number = element.text().removePrefix("Ep ").toFloat()
        return episode
    }

    // Video
    override fun videoListRequest(episode: SEpisode): Request {
        val document = client.newCall(GET(baseUrl + episode.url)).execute().asJsoup()
        val iframe = document.select("iframe").attr("src")
        return GET(iframe)
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        return videosFromElement(document)
    }

    override fun videoListSelector() = "ul.list-server-items li"

    private fun videosFromElement(document: Document): List<Video> {
        val videoList = mutableListOf<Video>()
        val elements = document.select(videoListSelector())
        for (element in elements) {
            val url = element.attr("data-video")
            val location = element.ownerDocument().location()
            val videoHeaders = Headers.headersOf("Referer", location)
            when {
                url.contains("sbembed.com") || url.contains("sbembed1.com") || url.contains("sbplay.org") ||
                    url.contains("sbvideo.net") || url.contains("streamsb.net") || url.contains("sbplay.one") ||
                    url.contains("cloudemb.com") || url.contains("playersb.com") || url.contains("tubesb.com") ||
                    url.contains("sbplay1.com") || url.contains("embedsb.com") || url.contains("watchsb.com") ||
                    url.contains("sbplay2.com") || url.contains("japopav.tv") || url.contains("viewsb.com") ||
                    url.contains("sbfast") || url.contains("sbfull.com") || url.contains("javplaya.com") ||
                    url.contains("ssbstream.net") || url.contains("p1ayerjavseen.com") || url.contains("sbthe.com")
                -> {
                    val videos = StreamSBExtractor(client).videosFromUrl(url, headers)
                    videoList.addAll(videos)
                }
                url.contains("dood") -> {
                    val video = DoodExtractor(client).videoFromUrl(url)
                    if (video != null) {
                        videoList.add(video)
                    }
                }
                url.contains("fembed.com") ||
                    url.contains("anime789.com") || url.contains("24hd.club") || url.contains("fembad.org") ||
                    url.contains("vcdn.io") || url.contains("sharinglink.club") || url.contains("moviemaniac.org") ||
                    url.contains("votrefiles.club") || url.contains("femoload.xyz") || url.contains("albavido.xyz") ||
                    url.contains("feurl.com") || url.contains("dailyplanet.pw") || url.contains("ncdnstm.com") ||
                    url.contains("jplayer.net") || url.contains("xstreamcdn.com") || url.contains("fembed-hd.com") ||
                    url.contains("gcloud.live") || url.contains("vcdnplay.com") || url.contains("superplayxyz.club") ||
                    url.contains("vidohd.com") || url.contains("vidsource.me") || url.contains("cinegrabber.com") ||
                    url.contains("votrefile.xyz") || url.contains("zidiplay.com") || url.contains("ndrama.xyz") ||
                    url.contains("fcdn.stream") || url.contains("mediashore.org") || url.contains("suzihaza.com") ||
                    url.contains("there.to") || url.contains("femax20.com") || url.contains("javstream.top") ||
                    url.contains("viplayer.cc") || url.contains("sexhd.co") || url.contains("fembed.net") ||
                    url.contains("mrdhan.com") || url.contains("votrefilms.xyz") || // url.contains("") ||
                    url.contains("embedsito.com") || url.contains("dutrag.com") || // url.contains("") ||
                    url.contains("youvideos.ru") || url.contains("streamm4u.club") || // url.contains("") ||
                    url.contains("moviepl.xyz") || url.contains("asianclub.tv") || // url.contains("") ||
                    url.contains("vidcloud.fun") || url.contains("fplayer.info") || // url.contains("") ||
                    url.contains("diasfem.com") || url.contains("javpoll.com") // url.contains("")

                -> {
                    val videos = FembedExtractor().videosFromUrl(url)
                    videoList.addAll(videos)
                }
                url.contains("streamtape") -> {
                    val video = StreamTapeExtractor(client).videoFromUrl(url)
                    if (video != null) {
                        videoList.add(video)
                    }
                }
            }
        }
        return videoList
    }

    override fun videoFromElement(element: Element) = throw Exception("not used")

    override fun videoUrlParse(document: Document) = throw Exception("not used")

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString("preferred_quality", null)
        if (quality != null) {
            val newList = mutableListOf<Video>()
            var preferred = 0
            for (video in this) {
                if (video.quality.contains(quality)) {
                    newList.add(preferred, video)
                    preferred++
                } else {
                    newList.add(video)
                }
            }
            return newList
        }
        return this
    }

    // Anime window
    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.thumbnail_url = "$baseUrl/${document.select("div.anime__details__pic").attr("data-setbg").removePrefix("./")}"
        anime.title = document.select("div.anime__details__title h3").text()
        anime.genre = document.select("div.col-lg-6.col-md-6:nth-of-type(1) ul li:nth-of-type(3)")
            .joinToString(", ") { it.text() }.replace("Genre:", "")
        anime.description = document.select("div.anime__details__text > p").text()
        document.select("div.col-lg-6.col-md-6:nth-of-type(2) ul li:nth-of-type(2)").text()
            ?.also { statusText ->
                when {
                    statusText.contains("Ongoing", true) -> anime.status = SAnime.ONGOING
                    statusText.contains("Completed", true) -> anime.status = SAnime.COMPLETED
                    else -> anime.status = SAnime.UNKNOWN
                }
            }
        return anime
    }

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val videoQualityPref = ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Preferred quality"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080", "720", "480", "360")
            setDefaultValue("1080")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }
        screen.addPreference(videoQualityPref)
    }
}
