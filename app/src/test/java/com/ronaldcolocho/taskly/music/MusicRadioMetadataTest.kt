package com.ronaldcolocho.taskly.music

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ronaldcolocho.taskly.data.music.parseMusicRadioMetadata
import org.junit.Assert.*
import org.junit.Test

class MusicRadioMetadataTest {
    private fun video(id: String, byline: String) = """{"playlistPanelVideoRenderer":{"videoId":"$id","title":{"runs":[{"text":"Song"}]},"lengthText":{"simpleText":"4:24"},"thumbnail":{"thumbnails":[{"url":"https://example.com/image.jpg","height":100}]},"shortBylineText":{"runs":[{"text":"LETRAS4K"}]},"longBylineText":{"runs":[$byline]}}}"""
    private fun response(vararg videos: String): String {
        val panel = JsonParser.parseString("""{"contents":[${videos.joinToString(",")}]}""")
        val content = JsonObject().apply { add("playlistPanelRenderer", panel) }
        val queue = JsonObject().apply { add("content", content) }
        return """{"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{"watchNextTabbedResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"musicQueueRenderer":$queue}}}]}}}}}"""
    }
    private fun artist(name: String, type: String = "MUSIC_PAGE_TYPE_ARTIST") = """{"text":"$name","navigationEndpoint":{"browseEndpoint":{"browseId":"UCfixture","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"$type"}}}}}"""

    @Test fun verifiedArtistIsTakenFromMusicNavigationNotTheChannelOrViewCounts() {
        val byline = listOf(artist("Romeo Santos"), """{"text":" • 420M visualizaciones • "}""", artist("Carlos Santana")).joinToString(",")
        val song = parseMusicRadioMetadata(response(video("bbbbbbbbbbb", byline)), "aaaaaaaaaaa").single()
        assertEquals("Romeo Santos、Carlos Santana", song.artist)
        assertEquals("", song.sourceChannel)
        assertEquals(264, song.durationSeconds)
    }

    @Test fun channelWithoutArtistMetadataStaysSeparateAndIsNeverPromotedToArtist() {
        for (byline in listOf("""{"text":"LETRAS4K"}""", artist("LETRAS4K", "MUSIC_PAGE_TYPE_USER_CHANNEL"))) {
            val song = parseMusicRadioMetadata(response(video("bbbbbbbbbbb", byline)), "aaaaaaaaaaa").single()
            assertEquals("", song.artist)
            assertEquals("LETRAS4K", song.sourceChannel)
        }
    }

    @Test fun duplicatesSeedAndInvalidItemsAreExcludedAndMalformedRadioFails() {
        val song = video("bbbbbbbbbbb", artist("Artist"))
        val result = parseMusicRadioMetadata(response(video("aaaaaaaaaaa", artist("Seed")), song, song,
            video("invalid", artist("Wrong"))), "aaaaaaaaaaa")
        assertEquals(listOf("youtube:bbbbbbbbbbb"), result.map { it.id })
        assertTrue(runCatching { parseMusicRadioMetadata("{}", "aaaaaaaaaaa") }.isFailure)
    }
}
