package com.ehnmark.fikasug

import android.os.Bundle
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
import java.util.LinkedList
import android.os.SystemClock
import rx.Observable
import android.app.Activity
import android.widget.ListView
import android.graphics.Color


public class VenueListActivity : Activity() {

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
        val venues = r.response.groups?.let {
            it.flatMap  { it.items.map { it.venue  } }
        }
        var items = venues?.let {
            it
                .sortBy { it.location?.distance ?: Integer.MAX_VALUE }
                .map { VenueViewModel(it) }.toArrayList()
        } ?: ArrayList<VenueViewModel>()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.venue_list)

        //val view = RecyclerView()
        val view = findViewById(R.id.venueListView) as ListView
        view.setBackgroundColor(Color.GREEN)

        adaptor = ArrayAdapter(this, android.R.layout.simple_list_item_1, viewModels)
        view.setAdapter(adaptor)

        view.setOnItemClickListener { (adapterView, view, pos, id) ->
            val item = adapterView.getItemAtPosition(pos) as VenueViewModel
            handleVenueTouch(item.venue)
        }
    }

    private fun goodEnoughLocation(loc: Location): Boolean {
        val oneSecondNanos = 10e9
        val nanosThreshold = 10 * oneSecondNanos
        val accuracyThreshold = 40
        val ageNanos = SystemClock.elapsedRealtimeNanos() - loc.getElapsedRealtimeNanos()
        return ageNanos < nanosThreshold && loc.getAccuracy() < accuracyThreshold
    }

    private fun handleLocationFix(loc: Location): Observable<Result> {
        val proxy = Foursquare()
        val ll = "${loc.getLatitude()},${loc.getLongitude()}"
        Toast.makeText(this, "Got location; looking for cafes...",  Toast.LENGTH_SHORT).show()
        return proxy.explore("", ll)
    }

    override fun onStart() {
        super.onStart()
        val locationTimeoutMillis = 3000
        var subscription = bestLocation(this)
                .subscribeOn(AndroidSchedulers.mainThread())
                .timestamp()
                .takeUntil { it.getTimestampMillis() > locationTimeoutMillis || goodEnoughLocation(it.getValue()) }
                .last()
                .map { it.getValue() }
                .flatMap { handleLocationFix(it) }
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
