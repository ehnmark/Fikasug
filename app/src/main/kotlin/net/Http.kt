package com.ehnmark.fikasug.net

import java.net.URLEncoder
import com.squareup.okhttp.Call
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import rx.Observable
import com.squareup.okhttp.Callback
import com.squareup.okhttp.Response
import android.util.Log
import com.ehnmark.fikasug.TAG
import java.io.IOException
import rx.subscriptions.Subscriptions

private fun toQueryString(m: Map<String, String>): String {
    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    return m.toList().fold("") { (acc, pair) -> "$acc&${pair.first}=${enc(pair.second)}" }
}

private fun toObservable(call: Call): Observable<String> {
    return Observable.create<String> { obs ->
        call.enqueue(object : Callback {
                    override fun onResponse(res: Response) {
                        val body = res.body().string()
                        obs.onNext(body)
                        obs.onCompleted()
                    }
                    override fun onFailure(req: Request, ex: IOException) {
                        obs.onError(ex)
                    }
                })
        Subscriptions.empty()
    }
}

fun httpGet(uri: String, params: Map<String, String>) : Observable<String> {
    val client = OkHttpClient()
    val query = toQueryString(params)
    val url = "$uri?$query"
    val request = Request.Builder()
            .url(url)
            .build()
    return toObservable(client.newCall(request))
}