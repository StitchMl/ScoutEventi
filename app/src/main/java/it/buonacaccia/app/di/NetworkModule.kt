package it.buonacaccia.app.di

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import org.koin.dsl.module

val networkModule = module {
    single {
        val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .allEnabledTlsVersions()
            .allEnabledCipherSuites()
            .build()

        OkHttpClient.Builder()
            .connectionSpecs(listOf(spec, ConnectionSpec.CLEARTEXT))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}