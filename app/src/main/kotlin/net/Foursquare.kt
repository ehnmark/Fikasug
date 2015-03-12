package com.ehnmark.fikasug.net

import rx.Observable
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.databind.DeserializationFeature
import android.util.Log
import com.ehnmark.fikasug.TAG

data class Meta(val code: Int, val errorType: String?, val errorDetail: String?)
data class Hours(val isOpen: Boolean, val status: String?)
data class Location(val address: String?, val lat: Double?, val lng: Double?, val distance: Int?)
data class Venue(val id: String, val name: String?, val location: Location?, val rating: Double?, val ratingSignals: Int?, val hours: Hours?)
data class GroupItem(val venue: Venue)
data class Group(val type: String, val name: String, val items: List<GroupItem>)
data class Payload(val headerFullLocation: String?, val totalResults: Int?, val groups: List<Group>?)
data class Result(val meta: Meta, val response: Payload)

class FoursquareException(val msg: String) : Exception(msg)

class Foursquare()
{
    private val mapper = createMapper()

    private fun createMapper(): ObjectMapper {
        val mapper = ObjectMapper().registerKotlinModule()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return mapper
    }

    private fun exploreRaw(query: String, latLong: String) : Observable<String> {
        val baseUri = "https://api.foursquare.com/v2/venues/explore"
        val params = hashMapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "v" to "20140806",
                "m" to "foursquare",
                "section" to "coffee",
                "query" to query,
                "ll" to latLong)
        return httpGet(baseUri, params)
    }

    fun explore(query: String, latLong: String) : Observable<Result> {
        Log.i(TAG, "explore: $latLong")
        return exploreRaw(query, latLong)
                .map { mapper.readValue(it, javaClass<Result>()) }

    }
}