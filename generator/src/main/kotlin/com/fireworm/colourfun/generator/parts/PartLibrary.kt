package com.fireworm.colourfun.generator.parts

import kotlinx.serialization.json.Json

/**
 * All parts, loaded from the JSON files in `assets/parts/`. The generator is pure JVM, so callers
 * read the files (Android assets in the app, the source folder in tests) and pass their text.
 */
class PartLibrary(parts: List<Part>) {
    /** Sorted by id, so choosing a part by random index is the same on every device. */
    val parts: List<Part> = parts.sortedBy { it.id }

    init {
        val duplicates = parts.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate part ids: $duplicates" }
    }

    /** Parts of [category] usable in [template], in a stable order. */
    fun candidates(category: String, template: String): List<Part> =
        parts.filter { it.category == category && template in it.compatibleWith }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        /** Parses part files: the text of each `.json` file. */
        fun parse(files: List<String>) = PartLibrary(files.map { Part(json.decodeFromString<PartJson>(it)) })
    }
}
