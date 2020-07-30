package io.github.starwishsama.comet.utils

import cn.hutool.core.util.URLUtil
import com.google.gson.JsonParser
import io.github.starwishsama.comet.BotVariables
import io.github.starwishsama.comet.objects.pojo.PicSearchResult
import org.jsoup.Jsoup

object PictureSearchUtil {
    private const val sauceNaoApi = "https://saucenao.com/search.php?db=5&output_type=2&numres=3&url="
    private const val ascii2d = "https://ascii2d.net/search/url/"

    fun sauceNaoSearch(url: String): PicSearchResult {
        val encodedUrl = URLUtil.encode(url)
        val key = BotVariables.cfg.saucenaoApiKey
        val request = NetUtil.doHttpRequestGet(
            "$sauceNaoApi$encodedUrl${if (key != null && key.isNotEmpty()) "&api_key=$key" else ""}",
            5000
        )
        val result = request.executeAsync()

        if (result.isOk && result.header("Content-Type").contains("json")) {
            val body = result.body()
            try {
                val resultBody = JsonParser.parseString(body)
                if (resultBody.isJsonObject) {
                    val resultJson = resultBody.asJsonObject["results"].asJsonArray[0].asJsonObject
                    val similarity = resultJson["header"].asJsonObject["similarity"].asDouble
                    val pictureUrl = resultJson["header"].asJsonObject["thumbnail"].asString
                    val originalUrl = resultJson["data"].asJsonObject["ext_urls"].asJsonArray[0].asString
                    return PicSearchResult(pictureUrl, originalUrl, similarity, request.url)
                }
            } catch (e: Exception) {
                BotVariables.logger.error("[以图搜图] 在解析 API 传回的 json 时出现了问题", e)
                FileUtil.createErrorReportFile("picsearch", e, body, request.url)
            }
        }
        return PicSearchResult.emptyResult()
    }

    fun ascii2dSearch(url: String): PicSearchResult {
        val encodedUrl = URLUtil.encode(url)
        val request = NetUtil.doHttpRequestGet("$ascii2d$encodedUrl", 5000)
        val result = request.executeAsync()

        if (result.isOk) {
            val html = Jsoup.parse(result.body())
            val elements = html.body().getElementsByClass("container")
            val imgUrl: String =
                "https://ascii2d.net/" + elements.select(".image-box")[1].select("img")[0].attributes()["src"]
            val sources = elements.select(".info-box")[1].select("a")
            val original = sources[0].attributes()["href"]
            return PicSearchResult(imgUrl, original, -1.0, "$ascii2d$encodedUrl")
        }

        return PicSearchResult.emptyResult()
    }
}