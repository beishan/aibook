package com.aibook.android.core.network.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioApiContractTest {
    @Test
    fun offlineManifestRoundTripPreservesStableTrackIdentityAndBaseRevision() {
        val source = """{
            "id":10,"bookId":20,"title":"三体","name":"完整版","narrator":"演播者",
            "contentRevision":3,"durationMs":60000,
            "tracks":[{"id":101,"title":"第一集","durationMs":60000,"mimeType":"audio/mpeg","fileSize":12345,"fileHash":"sha256"}],
            "chapters":[{"id":1,"title":"第一章","segments":[{"trackId":101,"startMs":0,"endMs":60000}]}],
            "progress":{"revision":8,"trackId":101,"positionMs":12000,"status":"LISTENING"},
            "futureServerField":"ignored"
        }"""
        val manifest = AudioJson.decode(source)
        val restored = AudioJson.decode(AudioJson.encode(manifest))
        assertEquals(101L,restored.tracks.single().id)
        assertEquals(8L,restored.progress.revision)
        assertEquals("sha256",restored.tracks.single().fileHash)
        assertEquals(3L,restored.contentRevision)
    }

    @Test
    fun offlineConflictRetryRequiresExplicitResolutionAndKeepsLocalPosition() {
        val local = AudioOfflineProgressDto("event",8,3,101,12000,
            intervals=listOf(AudioIntervalDto(1000,2000)))
        val pending = AudioJson.decodeProgress(AudioJson.encodeProgress(local))
        assertFalse(pending.resolveConflict)
        val chosen = pending.copy(baseRevision=9,resolveConflict=true)
        assertTrue(chosen.resolveConflict)
        assertEquals(12000L,chosen.positionMs)
        assertEquals(local.eventId,chosen.eventId)
        assertEquals(local.intervals,chosen.intervals)
    }
}
