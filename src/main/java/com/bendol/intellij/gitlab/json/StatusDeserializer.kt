package com.bendol.intellij.gitlab.json

import com.bendol.intellij.gitlab.model.Status
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class StatusDeserializer : JsonDeserializer<Status> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Status {
        return Status.valueOf(json.asString.uppercase())
    }
}