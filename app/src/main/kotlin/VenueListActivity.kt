package com.ehnmark.fikasug

import android.os.Bundle
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
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView.ViewHolder
import android.view.ViewGroup
import android.widget.TextView
import android.view.LayoutInflater
import android.support.v7.widget.DefaultItemAnimator
import android.view.View
import android.widget.RatingBar


public class VenueListActivity : Activity() {

    private var viewModels = ArrayList<VenueViewModel>()
    private var adapter: VenueListAdapter? = null
    private var subscriptions = LinkedList<rx.Subscription>()

    class VenueViewModel(val venue: Venue) {
        override fun toString(): String {
            val d = venue.location?.distance?.let { " ($it m)" } ?: ""
            return "${venue.name}${d}"
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
        adapter?.notifyDataSetChanged()
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
        val name = venue.name
        if(lat != null && lon != null && name != null) {
            val uri = "geo:$lat,$lon?q=$lat,$lon($name)"
            startActivity(Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)))
        }
    }

    class ViewModelHolder(
            private val view: View,
            private val clickAction: (VenueViewModel) -> Unit)
    : ViewHolder(view) {
        private val textView = view.findViewById(R.id.venue_name) as? TextView
        private val rating = view.findViewById(R.id.venue_rating) as? RatingBar
        private var viewModel: VenueViewModel? = null
        {
            view.setOnClickListener {
                viewModel?.let { clickAction(it) }
            }
        }
        private fun updateView() {
            if(viewModel != null && textView != null && rating != null) {
                textView.setText(viewModel.toString())
                rating.setIsIndicator(true)
                val value = viewModel?.venue?.rating
                if(value != null) {
                    val value = value/2f
                    rating.setVisibility(View.VISIBLE)
                    rating.setRating(value.toFloat())
                } else {
                    rating.setVisibility(View.INVISIBLE)
                }
            }

        }
        fun setViewModel(vm: VenueViewModel?) {
            viewModel = vm
            updateView()
        }
    }

    class VenueListAdapter(
            private val items: List<VenueViewModel>,
            private val clickAction: (VenueViewModel) -> Unit)
    : RecyclerView.Adapter<ViewModelHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup?, pos: Int): ViewModelHolder? {
            return parent?.let {
                LayoutInflater
                        .from(it.getContext())
                        .inflate(R.layout.venue_item, parent, false)

            }?.let {
                ViewModelHolder(it, clickAction)
            }
        }

        override fun onBindViewHolder(holder: ViewModelHolder?, pos: Int) {
            if(pos < items.size()) {
                val item = items.get(pos)
                holder?.setViewModel(item)
            }
        }

        override fun getItemCount(): Int {
            return items.size()
        }
    }

    private fun setupRecyclerView() {
        val view = findViewById(R.id.venue_recycler_view) as? RecyclerView
        view?.let {
            it.setLayoutManager(LinearLayoutManager(this))
            it.setItemAnimator(DefaultItemAnimator())
            it.setHasFixedSize(true)

            adapter = VenueListAdapter(viewModels, { handleVenueTouch(it.venue) })
            it.setAdapter(adapter)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.venue_list)

        setupRecyclerView()
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
