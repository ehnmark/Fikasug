package com.ehnmark.fikasug

import android.os.Bundle
import com.ehnmark.fikasug.net.Foursquare
import rx.android.schedulers.AndroidSchedulers
import com.ehnmark.fikasug.net.Result
import java.util.ArrayList
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
import android.app.ProgressDialog
import android.support.v4.widget.SwipeRefreshLayout


public class VenueListActivity : Activity() {

    private var swipeLayout: SwipeRefreshLayout? = null
    private var viewModels = ArrayList<VenueViewModel>()
    private var adapter: VenueListAdapter? = null
    private var subscriptions = LinkedList<rx.Subscription>()
    private var progressDialog: ProgressDialog? = null

    class VenueViewModel(val venue: Venue) {
        override fun toString(): String {
            val d = venue.location?.distance?.let { " ($it m)" } ?: ""
            return "${venue.name}${d}"
        }
    }

    fun handleResult(r: Result) {
        progressDialog?.dismiss()
        swipeLayout?.setRefreshing(false)
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

        Toast.makeText(this, "Found ${viewModels.size()} cafes",  Toast.LENGTH_SHORT).show()
    }

    fun handleError(ex: Throwable) {
        progressDialog?.dismiss()
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
                    val scaleOneToFive = value/2f
                    rating.setVisibility(View.VISIBLE)
                    rating.setRating(scaleOneToFive.toFloat())
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

    private fun setupSwipeLayout() {
        swipeLayout = findViewById(R.id.swipe_container) as? SwipeRefreshLayout
        swipeLayout?.setOnRefreshListener {
            refreshVenues()
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
        progressDialog?.setMessage("looking for cafes…")
        return proxy.explore("", ll)
    }

    private fun startProgress() {
        progressDialog = ProgressDialog.show(
                this,
                "Retrieving results",
                "Finding location…",
                true,
                true)

        progressDialog?.setOnCancelListener { unsubscribeAll() }
    }

    private fun unsubscribeAll() {
        subscriptions.forEach {
            if (! it.isUnsubscribed()) it.unsubscribe()
        }
        subscriptions.clear()
    }

    private fun refreshVenues() {
        swipeLayout?.setRefreshing(true)
        val locationTimeoutMillis = 3000
        var subscription = bestLocation(this)
                .timestamp()
                .takeUntil { it.getTimestampMillis() > locationTimeoutMillis || goodEnoughLocation(it.getValue()) }
                .last()
                .map { it.getValue() }
                .flatMap { handleLocationFix(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ handleResult(it) }, { handleError(it) })

        subscriptions.add(subscription)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.venue_list)
        setupSwipeLayout()
        setupRecyclerView()
        startProgress()
        refreshVenues()
    }

    override fun onStop() {
        super.onStop()
        progressDialog?.dismiss()
        unsubscribeAll()
    }
}
