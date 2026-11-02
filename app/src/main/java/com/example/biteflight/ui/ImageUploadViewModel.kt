package com.example.biteflight.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.biteflight.utils.Resource
import com.example.biteflight.utils.S3Uploader

/** Keeps an in-flight item photo upload attached to the screen across rotation. */
class ImageUploadViewModel : ViewModel() {
    private val result = MutableLiveData<Resource<String>>()
    val state: LiveData<Resource<String>> = result

    fun upload(context: Context, uri: Uri) {
        if (result.value is Resource.Loading) return
        result.value = Resource.Loading
        S3Uploader.uploadImage(context.applicationContext, uri, "meals") { result.postValue(it) }
    }
}
