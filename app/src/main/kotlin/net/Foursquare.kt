package com.ehnmark.fikasug.net

import rx.Observable
import rx.subscriptions.Subscriptions
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.databind.DeserializationFeature
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Call
import com.squareup.okhttp.Response
import com.squareup.okhttp.Callback
import java.io.IOException
import org.apache.http.client.utils.URLEncodedUtils
import java.net.URLEncoder

data class Meta(val code: Int, val errorType: String?, val errorDetail: String?)
data class Hours(val isOpen: Boolean, val status: String?)
data class Location(val address: String?, val lat: Double?, val lng: Double?, val distance: Int?)
data class Venue(val id: String, val name: String?, val location: Location?, val rating: Double?, val ratingSignals: Int?, val hours: Hours?)
data class GroupItem(val venue: Venue)
data class Group(val type: String, val name: String, val items: List<GroupItem>)
data class Payload(val headerFullLocation: String?, val totalResults: Int?, val groups: List<Group>?)
data class Result(val meta: Meta, val response: Payload)

class FoursquareException(val msg: String) : Exception(msg)

class Foursquare(private val language: String = "ja-JP")
{
    private val mapper = createMapper()

    private fun createMapper(): ObjectMapper {
        val mapper = ObjectMapper().registerKotlinModule()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return mapper
    }

    private fun toQueryString(m: Map<String, String>): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        return m.toList().fold("") { (acc, pair) -> "$acc&${pair.first}=${enc(pair.second)}" }
    }

    private fun createBuilder(query: String, latLong: String) : Call {
        val baseUri = "https://api.foursquare.com/v2/venues/explore"
        val client = OkHttpClient()
        val params = hashMapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "v" to "20140806",
                "m" to "foursquare",
                "section" to "coffee",
                "query" to query,
                "ll" to latLong)
        val query = toQueryString(params)
        val url = "$baseUri?$query"
        val request = Request.Builder()
                .addHeader("Accept-Language", language)
                .url(url)
                .build()
        return client.newCall(request)
    }

    private fun parseResponse(json: String, mapper: ObjectMapper): Result {
        return mapper.readValue(json, javaClass<Result>())
    }

    fun explore(query: String, latLong: String) : Observable<Result> {
        return Observable.create<Result> { obs ->
            createBuilder(query, latLong)
                    .enqueue(object : Callback {
                        override fun onResponse(res: Response) {
                            val body = res.body().string()
                            val result = mapper.readValue(body, javaClass<Result>())
                            if(result.meta.code == 200) {
                                obs.onNext(result)
                                obs.onCompleted()
                            } else {
                                obs.onError(FoursquareException(result.meta.errorDetail ?: "unknown error"))
                            }
                        }
                        override fun onFailure(req: Request, ex: IOException) {
                            obs.onError(ex)
                        }
                    })
            Subscriptions.empty()
        }
    }
}