package com.ehnmark.fikasug

import android.os.Bundle
import android.app.ListActivity
import android.widget.ArrayAdapter
import com.ehnmark.fikasug.net.Foursquare
import rx.android.schedulers.AndroidSchedulers
import com.ehnmark.fikasug.net.Result
import java.util.ArrayList
import rx.schedulers.Schedulers
import android.app.AlertDialog
import android.util.Log
import com.ehnmark.fikasug.net.Venue
import android.widget.Toast
import android.net.Uri
import android.content.Intent
import android.location.Location
import java.util.concurrent.TimeUnit
import java.util.LinkedList


public class VenueListActivity : ListActivity() {

    private var viewModels = ArrayList<VenueViewModel>()
    private var adaptor: ArrayAdapter<VenueViewModel>? = null
    private var subscriptions = LinkedList<rx.Subscription>()

    class VenueViewModel(val venue: Venue) {
        override fun toString(): String {
            val d = venue.location?.distance?.let { " ($it m)" } ?: ""
            val r = venue.rating?.let { ": $it" } ?: ""
            return "${venue.name}${d}${r}"
        }
    }

    fun handleResult(r: Result) {
        val venues = r.response.groups?.let { it.flatMap  { it.items.map { it.venue  } } }
        var items = venues?.let { it.sortBy { it.location?.distance ?: Integer.MAX_VALUE }.map { VenueViewModel(it) }.toArrayList() } ?: ArrayList<VenueViewModel>()
        viewModels.clear()
        viewModels.addAll(items)
        adaptor?.notifyDataSetChanged()
    }

    fun handleError(ex: Throwable) {
        Log.e(TAG, "Error", ex)
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(ex.getMessage())
            .show()
    }

    private fun handleVenueTouch(venue: Venue) {
        val lat = venue.location?.lat
        val lon = venue.location?.lng
        if(lat != null && lon != null) {
            val uri = "geo:$lat,$lon"
            startActivity(Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)))
        }
    }

    fun bestLocation(one: Location, two: Location): Location {
        val timeThreshold = 1000 * 60 * 1
        if(one.getAccuracy() > two.getAccuracy()) return two
        val timeDelta = one.getTime() - two.getTime()
        if(timeDelta > timeThreshold) return one
        return one
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this

        adaptor = ArrayAdapter(this, android.R.layout.simple_list_item_1, viewModels)
        getListView().setAdapter(adaptor)

        getListView().setOnItemClickListener { (adapterView, view, pos, id) ->
            val item = adapterView.getItemAtPosition(pos) as VenueViewModel
            Toast.makeText(context, "click: $item", Toast.LENGTH_SHORT).show()
            handleVenueTouch(item.venue)
        }
    }

    override fun onStart() {
        super.onStart()

        val proxy = Foursquare()
        var subscription = requestLocation(this)
                .subscribeOn(AndroidSchedulers.mainThread())
                .scan { (a, b) -> bestLocation(a, b) }
                .throttleLast(3, TimeUnit.SECONDS)
                .map { "${it.getLatitude()},${it.getLongitude()}" }
                .flatMap { proxy.explore("", it) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ handleResult(it) }, { handleError(it) })

        subscriptions.add(subscription)
    }

    override fun onStop() {
        super.onStop()

        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()
    }
}
