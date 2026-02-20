package it.buonacaccia.app.di

import android.os.Build
import it.buonacaccia.app.BuildConfig
import okhttp3.ConnectionSpec
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.koin.dsl.module
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, List<Cookie>>() // host -> cookies

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host].orEmpty()
}

val networkModule = module {
    single<CookieJar> { InMemoryCookieJar() }

    single {
        val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .allEnabledTlsVersions()
            .allEnabledCipherSuites()
            .build()

        OkHttpClient.Builder()
            .connectionSpecs(listOf(spec, ConnectionSpec.CLEARTEXT))
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cookieJar(get())
            .addInterceptor { chain ->
                val localeTag = Locale.getDefault().toLanguageTag()
                val ua = "ScoutEventi/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})"

                val req = chain.request().newBuilder()
                    .header("User-Agent", ua)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", localeTag)
                    .build()

                val resp = chain.proceed(req)
                Timber.v("HTTP %d %s", resp.code, resp.request.url)
                resp
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}