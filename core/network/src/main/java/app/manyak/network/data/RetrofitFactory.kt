package app.manyak.network.data

import app.manyak.network.data.di.DataLayerConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

fun retrofit(
    client: OkHttpClient,
    config: DataLayerConfig,
    json: Json,
): Retrofit =
    Retrofit
        .Builder()
        .baseUrl(config.apiBaseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
