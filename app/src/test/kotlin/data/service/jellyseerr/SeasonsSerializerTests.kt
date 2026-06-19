package org.jellyfin.androidtv.data.service.jellyseerr

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SeasonsSerializerTests : FunSpec({
	val json = Json { ignoreUnknownKeys = true }
	val serializer = SeasonsSerializer()

	test("deserialize \"all\" string") {
		val result = json.decodeFromString(serializer, "\"all\"")
		result shouldBe Seasons.All
	}

	test("deserialize season list") {
		val result = json.decodeFromString(serializer, "[1, 2, 3]")
		result shouldBe Seasons.List(listOf(1, 2, 3))
	}

	test("serialize and deserialize round-trip for list") {
		val original = Seasons.List(listOf(1, 4))
		val encoded = json.encodeToString(serializer, original)
		val decoded = json.decodeFromString(serializer, encoded)
		decoded shouldBe original
	}

	test("serialize and deserialize round-trip for all") {
		val original = Seasons.All
		val encoded = json.encodeToString(serializer, original)
		val decoded = json.decodeFromString(serializer, encoded)
		decoded shouldBe original
	}

	test("reject invalid string value") {
		shouldThrow<SerializationException> {
			json.decodeFromString(serializer, "\"none\"")
		}
	}
})
