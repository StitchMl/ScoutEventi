package it.buonacaccia.app.di

import android.os.Build
import it.buonacaccia.app.BuildConfig
import okhttp3.ConnectionSpec
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.koin.dsl.module
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit

private class InMemoryCookieJar : CookieJar {
    private val lock = Any()
    private val store = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            store.removeAll { existing ->
                existing.expiresAt <= now || cookies.any { fresh ->
                    fresh.name == existing.name &&
                        fresh.domain == existing.domain &&
                        fresh.path == existing.path
                }
            }
            store += cookies.filter { it.expiresAt > now }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            store.removeAll { it.expiresAt <= now }
            return store.filter { it.matches(url) }
        }
    }
}

private class RequestHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val localeTag = Locale.getDefault().toLanguageTag()
        val userAgent = "ScoutEventi/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})"

        val request = chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", localeTag)
            .build()

        val response = chain.proceed(request)
        Timber.v("HTTP %d %s", response.code, response.request.url)
        return response
    }
}

val networkModule = module {
    single<CookieJar> { InMemoryCookieJar() }
    single<Interceptor> { RequestHeadersInterceptor() }

    single<OkHttpClient> {
        val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .allEnabledTlsVersions()
            .allEnabledCipherSuites()
            .build()
        val cookieJar: CookieJar = get()
        val requestHeadersInterceptor: Interceptor = get()

        OkHttpClient.Builder()
            .connectionSpecs(listOf(spec))
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cookieJar(cookieJar)
            .addInterceptor(requestHeadersInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
