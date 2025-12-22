package com.example.photosync.di

import com.example.photosync.data.remote.GoogleDriveApi
import com.example.photosync.data.remote.GooglePhotosApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://photoslibrary.googleapis.com/") // Base URL mặc định, sẽ override khi gọi Drive
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGooglePhotosApi(retrofit: Retrofit): GooglePhotosApi {
        return retrofit.create(GooglePhotosApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGoogleDriveApi(okHttpClient: OkHttpClient): GoogleDriveApi {
        // Drive API dùng base URL khác
        val driveRetrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/drive/v3/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return driveRetrofit.create(GoogleDriveApi::class.java)
    }
}
