package com.matchalab.sublog_api.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream

inline fun <reified T : Any> readJsonList(inputStream: InputStream): List<T> {
    val reader = inputStream.bufferedReader()
    val listType = object : TypeToken<List<T>>() {}.type
    return Gson().fromJson(reader, listType)
}