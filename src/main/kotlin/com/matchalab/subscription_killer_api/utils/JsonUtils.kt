package com.matchalab.subscription_killer_api.utils

import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.model.Message
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream

inline fun <reified T : Any> readJsonList(inputStream: InputStream): List<T> {
    val reader = inputStream.bufferedReader()
    val listType = object : TypeToken<List<T>>() {}.type
    return Gson().fromJson(reader, listType)
}

fun readMessages(inputStream: InputStream): List<Message> {
    val factory = GsonFactory.getDefaultInstance()
    val parser = factory.createJsonParser(inputStream)
    val messages = mutableListOf<Message>()
    parser.parseArray(messages, Message::class.java)
    return messages
}