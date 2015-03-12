package com.ehnmark.fikasug

import rx.Observable
import android.location.Location
import android.content.Context
import android.location.LocationManager
import android.location.LocationListener
import android.os.Bundle
import rx.subscriptions.Subscriptions
import android.util.Log

fun requestLocation(context: Context): Observable<Location> {
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

        locationManager.getProviders(true).forEach {
            obs.onNext(locationManager.getLastKnownLocation(it))
            locationManager.requestLocationUpdates(it, 0, 0f, listener)
        }

        obs.add(Subscriptions.create {
            Log.i(TAG, "removing location subscription")
            locationManager.removeUpdates(listener) })
    }
}