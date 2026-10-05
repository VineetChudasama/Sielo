package com.sielo.music.core.recommendations.autoplay

import com.sielo.music.core.network.models.SieloTrack
import java.util.Locale

enum class SongLanguage {
    HINDI,
    PUNJABI,
    SOUTH_INDIAN,
    ENGLISH,
    KOREAN,
    LATIN,
    OTHER
}

enum class SongVibe {
    ROMANTIC,
    SMOOTH_CALM,
    LOFI,
    RAP_HIPHOP,
    ENERGETIC_PARTY,
    POP
}

object SongClassifier {

    private val DEVANAGARI_REGEX = Regex("[\\u0900-\\u097F]")
    private val BENGALI_REGEX = Regex("[\\u0980-\\u09FF]")
    private val GUJARATI_REGEX = Regex("[\\u0A80-\\u0AFF]")
    private val ORIYA_REGEX = Regex("[\\u0B00-\\u0B7F]")
    private val URDU_ARABIC_REGEX = Regex("[\\u0600-\\u06FF]")
    private val GURMUKHI_REGEX = Regex("[\\u0A00-\\u0A7F]")
    private val SOUTH_INDIAN_REGEX = Regex("[\\u0B80-\\u0BFF\\u0C00-\\u0C7F\\u0C80-\\u0CFF\\u0D00-\\u0D7F]")
    private val HANGUL_REGEX = Regex("[\\uAC00-\\uD7AF\\u1100-\\u11FF]")

    private val HINDI_KEYWORDS = setOf(
        "dil", "ishq", "pyaar", "pyar", "tere", "teri", "mera", "meri", "hum", "tum",
        "humsafar", "raat", "raatan", "aankhon", "naina", "barsaat", "baarishein",
        "zindagi", "sanam", "jaan", "khuda", "sukoon", "shiddat", "kasam", "duniya",
        "afreen", "fitoor", "kahaani", "kahani", "chaha", "deewana", "aasman", "hawa", "sajna",
        "rooh", "channa", "mehboob", "tujhe", "apna", "apne", "saath", "chal",
        "kabhi", "pehla", "kuch", "koi", "khwab", "dard", "yaad", "judai", "jeena",
        "marne", "rang", "suroor", "waada", "khabar", "jogan", "soch", "akhiyaan",
        "saajna", "maahi", "ve", "tu", "hai", "yeh", "woh", "nahin", "kyun", "kesariya",
        "tumhi", "kasoor", "alvida", "musafir", "muskurane", "chupke", "duaa", "dua",
        "shayad", "bekhayali", "bol", "tera", "rabba", "khairiyat", "ghungroo", "chale",
        "taaj", "taj", "sunehra", "sunehre", "sunehri", "afsana", "afsanay", "khoya",
        "dhundhta", "dhundhla", "bematlab", "choo lo", "choolo", "waqt", "parinda",
        "safarnama", "manzil", "intezaar", "intezar", "khwaab", "khwahish", "khwabon",
        "junoon", "mohabbat", "dastaan", "fariyaad", "jazbaat", "ehsaas", "nazrein",
        "hawayein", "badal", "saaya", "tasveer", "tamanna", "khushboo", "umeed", "jahan",
        "shikwa", "rehnuma", "mann", "banjara", "sufi", "malang", "ibadat", "deewangi",
        "aashiqui", "aashiq", "jogi", "jogiya", "ranjha", "piya", "sawariya", "tarana",
        "geet", "awaaz", "awaz", "chalo", "dooriyan", "doorie", "kareeb", "paas",
        "lamha", "lamhe", "zamana", "chaand", "chand", "sitare", "khamoshi", "mishri",
        "husn", "gul", "faasle", "faasley", "juda", "hasrat", "kashmakash", "nazar"
    )

    private val PUNJABI_KEYWORDS = setOf(
        "kudi", "munda", "yaari", "pind", "jatt", "sohneya", "gedi", "wakhra",
        "gabru", "chobbar", "bapu", "punjab", "sohna", "suit", "lahore", "jatti",
        "akh", "surma", "mittran", "mitran", "yaar", "patiala", "jalandhar", "amritsar",
        "cheques", "dhol", "bhangra", "gidda", "boliyan", "tappe", "koka", "jutti",
        "chitta", "paranda", "jhanjhar", "taare", "mutiyare", "challa", "jugni",
        "pagri", "turban", "sardaar", "sardar", "kharku", "pendu", "velly", "badmashi",
        "vailpuna", "dunali", "riflan", "asla", "scorpio", "thar", "dhillon", "dosanjh",
        "aujla", "moosewala", "talwiinder"
    )

    private val LATIN_KEYWORDS = setOf(
        "amor", "te quiero", "corazon", "noche", "baila", "ella", "beso", "loco",
        "loca", "vida", "suave", "reggaeton", "playa", "amiga", "senorita", "despacito",
        "mamacita", "bonita", "contigo", "siempre"
    )

    private val HINDI_ARTISTS = listOf(
        "lost stories", "zaeden", "ritviz", "the local train", "twin strings", "when chai met toast",
        "taba chake", "lifafa", "peter cat recording co", "yellow diary", "the yellow diary",
        "anand bhaskar collective", "achint", "sanah moidutty", "shirley setia", "jonita gandhi",
        "arjun kanungo", "nikhita gandhi", "yashraj", "karan kanchan", "sez on the beat", "sickflip",
        "nucleya", "papon", "parvaaz", "aswekeepsearching", "kushagra", "aditya a", "aditya rikhari",
        "raghav chaitanya", "osho jain", "madhur sharma", "rahul jain", "bayaan", "mitraz",
        "ali sethi", "abdul hannan", "kaavish", "kaifi khalil", "hasan raheem", "asif ballaj", "shae gill",
        "anuv jain", "prateek kuhad", "jasleen royal", "lucky ali", "sanam", "outstation",
        "arijit singh", "arijit", "atif aslam", "atif", "shreya ghoshal", "shreya",
        "jubin nautiyal", "jubin", "mohit chauhan", "armaan malik", "armaan", "amal mallik", "amaal mallik",
        "vishal mishra", "darshan raval", "sonu nigam", "pritam", "sachin-jigar", "sachin jigar",
        "mithoon", "shankar-ehsaan-loy", "shankar ehsaan loy", "amit trivedi", "neha kakkar",
        "sunidhi chauhan", "sunidhi", "javed ali", "akhil sachdeva", "sachet tandon", "sachet",
        "parampara", "neeti mohan", "akasa", "kanika kapoor", "tulsi kumar", "dhvani bhanushali",
        "shilpa rao", "ash king", "divine", "seedhe maut", "kr\$na", "krsna", "mc stan", "raftaar",
        "badshah", "emiway bantai", "emiway", "paradox", "bella", "ikka", "fotty seven",
        "young stunners", "talha anjum", "talhah yunus", "karma", "epr", "raga", "king",
        "dino james", "rawal", "bharg", "kk", "kumar sanu", "alka yagnik", "udit narayan",
        "kishore kumar", "lata mangeshkar", "mohammed rafi", "rafi", "babul supriyo", "shaan",
        "mustafa zahid", "falak shabir", "roop kumar rathod", "pankaj udhas", "jagjit singh",
        "ghulam ali", "rahat fateh ali khan", "nusrat fateh ali khan", "nusrat", "sabri brothers",
        "abida parveen", "salim-sulaiman", "salim sulaiman", "vishal-shekhar", "vishal shekhar",
        "vishal dadlani", "shekhar ravjiani", "himesh reshammiya", "himesh", "mika singh", "mika",
        "coke studio", "t-series", "zee music", "sony music india", "yrf", "saregama", "tips official"
    )

    private val PUNJABI_ARTISTS = listOf(
        "ap dhillon", "diljit dosanjh", "diljit", "karan aujla", "sidhu moose", "moose wala",
        "shubh", "talwiinder", "gurinder gill", "amrit maan", "jassie gill", "harrdy sandhu",
        "prem dhillon", "the prophec", "prophec", "b praak", "jaani", "mankirt aulakh", "jass manak",
        "ammy virk", "parmish verma", "sidhu", "jordan sandhu", "kulwinder billa", "garry sandhu",
        "jasmine sandlas", "sunanda sharma", "nimrat khaira", "baani sandhu", "jenny johal",
        "korala maan", "hustinder", "arjan dhillon", "navaan sandhu", "cheema y", "gur sidhu",
        "bohemia", "imran khan", "pav dharia", "mickey singh", "sukhe", "yo yo honey singh", "honey singh", "guru randhawa"
    )

    private val SOUTH_INDIAN_ARTISTS = listOf(
        "anirudh ravichander", "anirudh", "sid sriram", "yuvan shankar raja", "yuvan",
        "harris jayaraj", "santhosh narayanan", "devi sri prasad", "dsp", "thaman s", "thaman",
        "sushin shyam", "g. v. prakash", "gv prakash", "hesham abdul wahab", "a.r. rahman",
        "ar rahman", "spb", "balasubrahmanyam", "k. s. chithra", "chithra", "chinmayi",
        "d. imman", "sean roldan", "ghibran", "vidyasagar", "ilaiyaraaja", "ilayaraja",
        "m. m. keeravani", "keeravaani", "hiphop tamizha", "sam c. s.", "leon james"
    )

    private val KPOP_ARTISTS = listOf(
        "bts", "blackpink", "stray kids", "newjeans", "twice", "seventeen", "le sserafim",
        "enhypen", "jung kook", "jimin", "aespa", "itzy", "ive", "txt", "tomorrow x together"
    )

    private val LATIN_ARTISTS = listOf(
        "bad bunny", "rauw alejandro", "j balvin", "maluma", "ozuna", "daddy yankee",
        "karol g", "rosalia", "rosalía", "feid", "myke towers", "anuel aa", "bizarrap"
    )

    // Vibe Keywords
    private val ROMANTIC_KEYWORDS = listOf(
        "romantic", "love", "ishq", "pyaar", "pyar", "dil", "humsafar", "romance", "ballad",
        "sweet", "beautiful", "heart", "soul", "crush", "wedding", "couples", "sweetheart",
        "kiss", "lovers", "deewana", "mohabbat", "sanam", "jaan", "tujhe", "kasam", "fitoor",
        "afreen", "forever", "mine", "baby", "darling", "kesariya", "raatan", "tum se hi",
        "apna bana le", "pehla pyaar", "shiddat", "saajna", "maahi", "hawayein", "sukoon"
    )

    private val SMOOTH_CALM_KEYWORDS = listOf(
        "soothing", "calm", "smooth", "acoustic", "unplugged", "piano", "soft", "gentle",
        "peaceful", "breeze", "sukoon", "sufi", "melancholy", "meditation", "relaxing",
        "silence", "serenade", "whisper", "baarishein", "kasoor", "ocean", "rain", "cozy",
        "deep", "downtempo", "halka", "dheere", "quiet", "chaand", "raat", "khoya",
        "choo lo", "waqt", "riha", "dil mere", "samundar", "mishri", "gul", "husn", "taaj", "sunehra"
    )

    private val LOFI_KEYWORDS = listOf(
        "lofi", "lo-fi", "chillhop", "chill", "study", "sleep", "slowed", "reverb",
        "bedroom", "night drive", "aesthetic", "beats", "flip", "relax", "tape",
        "lofi mix", "chill beats", "midnight focus", "coffee shop"
    )

    private val RAP_KEYWORDS = listOf(
        "rap", "hip-hop", "hiphop", "trap", "drill", "freestyle", "cypher", "flow",
        "bars", "diss", "808", "banger", "mc", "cypher", "machayenge", "asatoma",
        "vyanjan", "naam", "gully", "gang", "hood", "bambai", "desi hip hop"
    )

    private val PARTY_KEYWORDS = listOf(
        "remix", "club", "edm", "dance", "rock", "party", "banger", "bass", "dj",
        "speed", "hype", "pump", "beat", "techno", "house", "dubstep", "rave", "bounce",
        "drop", "electric", "festival", "nacho", "dhol", "thumka", "daru", "party all night"
    )

    fun isArtistCompatible(artistName: String, language: SongLanguage): Boolean {
        val lower = artistName.lowercase(Locale.ROOT).trim()
        return when (language) {
            SongLanguage.HINDI -> HINDI_ARTISTS.any { lower.contains(it) || it.contains(lower) }
            SongLanguage.PUNJABI -> PUNJABI_ARTISTS.any { lower.contains(it) || it.contains(lower) }
            SongLanguage.SOUTH_INDIAN -> SOUTH_INDIAN_ARTISTS.any { lower.contains(it) || it.contains(lower) }
            SongLanguage.KOREAN -> KPOP_ARTISTS.any { lower.contains(it) || it.contains(lower) }
            SongLanguage.LATIN -> LATIN_ARTISTS.any { lower.contains(it) || it.contains(lower) }
            SongLanguage.ENGLISH -> {
                !HINDI_ARTISTS.any { lower.contains(it) } &&
                !PUNJABI_ARTISTS.any { lower.contains(it) } &&
                !SOUTH_INDIAN_ARTISTS.any { lower.contains(it) } &&
                !KPOP_ARTISTS.any { lower.contains(it) } &&
                !LATIN_ARTISTS.any { lower.contains(it) }
            }
            else -> true
        }
    }

    fun detectLanguage(track: SieloTrack): SongLanguage {
        val title = track.title.lowercase(Locale.ROOT)
        val artist = track.artist.lowercase(Locale.ROOT)
        val album = (track.album ?: "").lowercase(Locale.ROOT)

        // 1. Script matching across title, artist and album
        val fullMeta = "${track.title} ${track.artist} ${track.album ?: ""}"
        if (DEVANAGARI_REGEX.containsMatchIn(fullMeta) ||
            BENGALI_REGEX.containsMatchIn(fullMeta) ||
            GUJARATI_REGEX.containsMatchIn(fullMeta) ||
            ORIYA_REGEX.containsMatchIn(fullMeta) ||
            URDU_ARABIC_REGEX.containsMatchIn(fullMeta)) return SongLanguage.HINDI

        if (GURMUKHI_REGEX.containsMatchIn(fullMeta)) return SongLanguage.PUNJABI
        if (SOUTH_INDIAN_REGEX.containsMatchIn(fullMeta)) return SongLanguage.SOUTH_INDIAN
        if (HANGUL_REGEX.containsMatchIn(fullMeta)) return SongLanguage.KOREAN

        // 2. Known artist recognition (highest priority for Latin-script Indian/Foreign music)
        if (HINDI_ARTISTS.any { artist.contains(it) || it.contains(artist) }) return SongLanguage.HINDI
        if (PUNJABI_ARTISTS.any { artist.contains(it) || it.contains(artist) }) return SongLanguage.PUNJABI
        if (SOUTH_INDIAN_ARTISTS.any { artist.contains(it) || it.contains(artist) }) return SongLanguage.SOUTH_INDIAN
        if (KPOP_ARTISTS.any { artist.contains(it) }) return SongLanguage.KOREAN
        if (LATIN_ARTISTS.any { artist.contains(it) }) return SongLanguage.LATIN

        // 3. Keyword / vocabulary matching
        val tokens = title.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        val hindiMatches = tokens.count { HINDI_KEYWORDS.contains(it) }
        val punjabiMatches = tokens.count { PUNJABI_KEYWORDS.contains(it) }
        val latinMatches = tokens.count { LATIN_KEYWORDS.contains(it) }

        if (hindiMatches >= 1) return SongLanguage.HINDI
        if (punjabiMatches >= 1) return SongLanguage.PUNJABI
        if (latinMatches >= 2) return SongLanguage.LATIN

        // Check if title contains multi-word Hindi keywords (e.g. "choo lo", "tum hi ho")
        if (listOf("choo lo", "tum hi ho", "tum se hi", "apna bana", "tera ban", "raatan lambiyan", "mai ni meriye", "aao balma").any { title.contains(it) }) {
            return SongLanguage.HINDI
        }

        // 4. Fallback to English for Latin-script audio
        return SongLanguage.ENGLISH
    }

    fun detectVibe(track: SieloTrack): SongVibe {
        val title = track.title.lowercase(Locale.ROOT)
        val artist = track.artist.lowercase(Locale.ROOT)
        val album = (track.album ?: "").lowercase(Locale.ROOT)
        val combined = "$title $artist $album"

        // 1. Check Lo-Fi
        if (LOFI_KEYWORDS.any { combined.contains(it) }) return SongVibe.LOFI

        // 2. Check Rap / Hip-Hop
        if (RAP_KEYWORDS.any { combined.contains(it) }) return SongVibe.RAP_HIPHOP
        if (listOf("divine", "seedhe maut", "kr\$na", "mc stan", "raftaar", "kendrick", "drake", "travis scott", "eminem", "bella", "paradox").any { artist.contains(it) }) {
            return SongVibe.RAP_HIPHOP
        }

        // 3. Check Smooth / Soothing / Calm (Lost Stories acoustic, Prateek Kuhad, Anuv Jain, etc.)
        if (SMOOTH_CALM_KEYWORDS.any { combined.contains(it) }) return SongVibe.SMOOTH_CALM
        if (listOf("anuv jain", "prateek kuhad", "jasleen royal", "lucky ali", "the local train", "osho jain", "lost stories", "zaeden", "twin strings", "taba chake", "cigarettes after sex", "laufey", "phoebe bridgers", "clairo", "hozier", "norah jones").any { artist.contains(it) }) {
            return SongVibe.SMOOTH_CALM
        }

        // 4. Check Romantic / Soulful
        if (ROMANTIC_KEYWORDS.any { combined.contains(it) }) return SongVibe.ROMANTIC
        if (listOf("arijit singh", "atif aslam", "shreya ghoshal", "jubin nautiyal", "ed sheeran", "stephen sanchez", "bruno mars", "darshan raval", "armaan malik").any { artist.contains(it) }) {
            return SongVibe.ROMANTIC
        }

        // 5. Check Energetic / Party
        if (PARTY_KEYWORDS.any { combined.contains(it) }) return SongVibe.ENERGETIC_PARTY
        if (listOf("garrix", "guetta", "marshmello", "alan walker", "dj snake", "badshah", "honey singh", "nucleya").any { artist.contains(it) }) {
            return SongVibe.ENERGETIC_PARTY
        }

        return SongVibe.POP
    }
}
