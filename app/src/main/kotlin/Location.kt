package com.ehnmark.fikasug

import rx.Observable
import android.location.Location
import android.content.Context
import android.location.LocationManager
import android.location.LocationListener
import android.os.Bundle
import rx.subscriptions.Subscriptions
import android.util.Log

private fun bestLocation(one: Location, two: Location): Location {
    val timeThreshold = 1000 * 60 * 2
    if(one.getAccuracy() > two.getAccuracy()) return two
    val timeDelta = one.getElapsedRealtimeNanos() - two.getElapsedRealtimeNanos()
    if(timeDelta > timeThreshold) return one
    return one
}

fun requestLocation(context: Context): Observable<Location> {
    Log.i(TAG, "creating location subscription")
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return Observable.create<Location> { obs ->
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (!obs.isUnsubscribed()) obs.onNext(loc)
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        }

        val enabledProviders = locationManager.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }

        if(! enabledProviders.any()) obs.onError(IllegalStateException("No location providers enabled!"))
        else {
            enabledProviders.forEach {
                val lastKnown = locationManager.getLastKnownLocation(it)
                lastKnown?.let { obs.onNext(it) }
                locationManager.requestLocationUpdates(it, 0, 0f, listener)
            }

            obs.add(Subscriptions.create {
                Log.i(TAG, "removing location subscription")
                locationManager.removeUpdates(listener) })
        }
    }
}

fun bestLocation(context: Context): Observable<Location> {
    return requestLocation(context).scan { (a, b) -> bestLocation(a, b) }
}