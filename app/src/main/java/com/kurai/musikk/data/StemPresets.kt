package com.kurai.musikk.data

/**
 * A stem preset is an ordered list of stem keys that the backend should
 * separate the source audio into. We model up to ten stems that demucs / HTDemucs
 * can emit; presets below pick common subsets so the user can quickly choose.
 *
 * Stem keys are stable lowercase identifiers — they are persisted to Firestore
 * (`stemKeys`) and used to label the row in the Stems screen.
 */
enum class StemKey(val displayName: String) {
    VOCALS("Vocals"),
    DRUMS("Drums"),
    BASS("Bass"),
    GUITAR("Guitar"),
    PIANO("Piano"),
    STRINGS("Strings"),
    OTHER("Other"),
    SYNTH("Synth"),
    PERCUSSION("Percussion"),
    BACKGROUND("Backing vocals");
}

/** A combination of stems to split the source audio into. */
data class StemPreset(
    val id: String,
    val displayName: String,
    val description: String,
    val stems: List<StemKey>,
)

/**
 * The full catalogue of stem presets the user can pick from in the
 * "Process song" dialog. Ordered from cheapest (1 stem) to fullest (10 stems).
 */
val STEM_PRESETS: List<StemPreset> = listOf(
    StemPreset("vocals_only", "Vocals only", "Just the lead vocal track", listOf(StemKey.VOCALS)),
    StemPreset("instrumental", "Instrumental", "Remove vocals — keep everything else",
        listOf(StemKey.DRUMS, StemKey.BASS, StemKey.GUITAR, StemKey.PIANO, StemKey.OTHER)),
    StemPreset("karaoke_2", "Karaoke · 2-stem", "Vocals vs. accompaniment",
        listOf(StemKey.VOCALS, StemKey.OTHER)),
    StemPreset("standard_4", "Standard · 4-stem", "Vocals · drums · bass · other",
        listOf(StemKey.VOCALS, StemKey.DRUMS, StemKey.BASS, StemKey.OTHER)),
    StemPreset("band_5", "Band · 5-stem", "Adds the guitar stem",
        listOf(StemKey.VOCALS, StemKey.DRUMS, StemKey.BASS, StemKey.GUITAR, StemKey.OTHER)),
    StemPreset("band_plus_6", "Band+ · 6-stem", "Adds the piano stem",
        listOf(StemKey.VOCALS, StemKey.DRUMS, StemKey.BASS, StemKey.GUITAR, StemKey.PIANO, StemKey.OTHER)),
    StemPreset("orchestral_7", "Orchestral · 7-stem", "Adds the strings stem",
        listOf(StemKey.VOCALS, StemKey.DRUMS, StemKey.BASS, StemKey.GUITAR, StemKey.PIANO, StemKey.STRINGS, StemKey.OTHER)),
    StemPreset("electronic_8", "Electronic · 8-stem", "Adds the synth stem",
        listOf(StemKey.VOCALS, StemKey.DRUMS, StemKey.BASS, StemKey.GUITAR, StemKey.PIANO, StemKey.STRINGS, StemKey.SYNTH, StemKey.OTHER)),
    StemPreset("rhythm_9", "Rhythm · 9-stem", "Adds the percussion stem",
        listOf(StemKey.VOCALS, StemKey.DRUMS, StemKey.BASS, StemKey.GUITAR, StemKey.PIANO, StemKey.STRINGS, StemKey.SYNTH, StemKey.PERCUSSION, StemKey.OTHER)),
    StemPreset("studio_10", "Studio · 10-stem", "All stems incl. backing vocals",
        StemKey.values().toList()),
)
