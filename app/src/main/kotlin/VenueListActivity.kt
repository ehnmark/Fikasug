package com.ehnmark.fikasug

import android.os.Bundle
import android.app.ListActivity
import android.widget.ProgressBar
import android.view.Gravity
import android.view.ViewGroup.LayoutParams
import android.widget.ArrayAdapter
import java.util.Arrays
import java.util.Collections
import com.ehnmark.fikasug.net.Foursquare
import rx.android.schedulers.AndroidSchedulers
import com.ehnmark.fikasug.net.Result
import java.util.ArrayList
import rx.schedulers.Schedulers
import android.app.AlertDialog
import android.util.Log
import com.ehnmark.fikasug.net.Venue
import android.location.LocationManager
import android.content.Context
import android.widget.Toast
import android.location.LocationListener
import android.location.Location
import android.widget.AdapterView
import android.view.View
import android.net.Uri
import android.content.Intent


public class VenueListActivity : ListActivity() {

    class VenueViewModel(val venue: Venue) {
        override fun toString(): String {
            val d = venue.location?.distance?.let { "$it m" } ?: "<unknown>"
            val r = venue.rating ?: "(unrated)"
            return "${venue.name} [$d]: ${r}"
        }
    }

    fun handleResult(r: Result) {
        val venues = r.response.groups?.let { it.flatMap  { it.items.map { it.venue  } } }
        val items = venues?.let { it.sortBy { it.location?.distance ?: 1000 }.map { VenueViewModel(it) }.toArrayList() } ?: ArrayList<VenueViewModel>()
        val adaptor = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        getListView().setAdapter(adaptor)
    }

    fun handleError(ex: Throwable) {
        Log.e("Rx", "Error", ex)
        AlertDialog.Builder(this)
            .setTitle("Rx error")
            .setMessage(ex.getMessage())
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val proxy = Foursquare()
        val context = this

        getListView().setOnItemClickListener { (adapterView, view, pos, id) ->
            val item = adapterView.getItemAtPosition(pos) as VenueViewModel
            Toast.makeText(context, "click: $item", Toast.LENGTH_SHORT).show()
            val lat = item.venue.location?.lat
            val lon = item.venue.location?.lng
            if(lat != null && lon != null) {
                val uri = "geo:$lat,$lon"
                startActivity(Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)))
            }

        }

        Toast.makeText(context, "Waiting for location...", Toast.LENGTH_SHORT).show()
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                val input = "${loc.getLatitude()},${loc.getLongitude()}"
                Toast.makeText(context, "Got location: $input", Toast.LENGTH_SHORT).show()
                Log.w("LOC", "onLocationChanged: $input ($loc)")
                proxy.explore("", input)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ handleResult(it) }, { handleError(it) })
            }
            override fun onProviderDisabled(provider: String) {
                Toast.makeText(context, "onProviderDisabled: $provider", Toast.LENGTH_SHORT).show()
                Log.w("LOC", "disabled: $provider")
            }
            override fun onProviderEnabled(provider: String) {
                Toast.makeText(context, "onProviderEnabled: $provider", Toast.LENGTH_SHORT).show()
                Log.w("LOC", "enabled: $provider")
            }
            override fun onStatusChanged (provider: String, status: Int, extras: Bundle) {
                Toast.makeText(context, "onStatusChanged: $provider", Toast.LENGTH_SHORT).show()
                Log.w("LOC", "onStatusChanged: $provider")
            }
        }, null)

    }
}
