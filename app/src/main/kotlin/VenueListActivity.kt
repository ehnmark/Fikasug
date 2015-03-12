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


public class VenueListActivity : ListActivity() {

    class VenueViewModel(val venue: Venue) {
        override fun toString(): String {
            val d = venue.location?.distance?.let { " ($it m)" } ?: ""
            val r = venue.rating?.let { ": $it" } ?: ""
            return "${venue.name}${d}${r}"
        }
    }

    fun handleResult(r: Result) {
        val venues = r.response.groups?.let { it.flatMap  { it.items.map { it.venue  } } }
        val items = venues?.let { it.sortBy { it.location?.distance ?: Integer.MAX_VALUE }.map { VenueViewModel(it) }.toArrayList() } ?: ArrayList<VenueViewModel>()
        val adaptor = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        getListView().setAdapter(adaptor)
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

    fun bestLocationWrapper(one: Location, two: Location): Location {
        val best = bestLocation(one, two)
        Log.i(TAG, "Locations: $one and $two --> $best")
        return best
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val proxy = Foursquare()
        val context = this

        getListView().setOnItemClickListener { (adapterView, view, pos, id) ->
            val item = adapterView.getItemAtPosition(pos) as VenueViewModel
            Toast.makeText(context, "click: $item", Toast.LENGTH_SHORT).show()
            handleVenueTouch(item.venue)
        }

        requestLocation(this)
                .subscribeOn(AndroidSchedulers.mainThread())
                .scan { (a, b) -> bestLocationWrapper(a, b) }
                .throttleLast(3, TimeUnit.SECONDS)
                .map { "${it.getLatitude()},${it.getLongitude()}" }
                .flatMap { proxy.explore("", it) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ handleResult(it) }, { handleError(it) }, { Log.i(TAG, "complete") })
    }
}
