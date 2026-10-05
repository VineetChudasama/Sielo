package com.sielo.music.core.recommendations.autoplay

import com.sielo.music.core.network.models.SieloTrack

object CuratedArtistClusters {

    // Hindi Clusters
    private val hindiRomanticCluster = listOf(
        "Arijit Singh", "Atif Aslam", "Shreya Ghoshal", "Jubin Nautiyal", "Mohit Chauhan",
        "Armaan Malik", "Vishal Mishra", "Darshan Raval", "Sonu Nigam", "KK", "Mithoon",
        "Pritam", "Sachin-Jigar", "Javed Ali", "Akhil Sachdeva", "Sachet Tandon", "Neeti Mohan",
        "Raghav Chaitanya", "Ash King", "Tulsi Kumar"
    )

    private val hindiSmoothCalmCluster = listOf(
        "Lost Stories", "Prateek Kuhad", "Anuv Jain", "Jasleen Royal", "Lucky Ali", "The Local Train",
        "Osho Jain", "Twin Strings", "Zaeden", "Ritviz", "Taba Chake", "When Chai Met Toast",
        "Aditya A", "Aditya Rikhari", "Sanam", "Raghav Chaitanya", "Mitraz", "Lifafa", "Outstation"
    )

    private val hindiLofiCluster = listOf(
        "Bollywood Lofi", "Lofi Fruits India", "Zaeden", "Lost Stories", "Anuv Jain",
        "Prateek Kuhad", "Twin Strings", "Mitraz", "Chillhop Hindi"
    )

    private val hindiRapCluster = listOf(
        "DIVINE", "Seedhe Maut", "KR\$NA", "MC Stan", "Raftaar", "Badshah",
        "Emiway Bantai", "Ikka", "Bella", "Paradox", "Fotty Seven", "Young Stunners",
        "Talha Anjum", "Karma", "EPR", "Raga", "King", "Dino James"
    )

    private val hindiPartyCluster = listOf(
        "Badshah", "Yo Yo Honey Singh", "DJ Chetas", "Nucleya", "Lost Stories",
        "Guru Randhawa", "Mika Singh", "Neha Kakkar", "Tony Kakkar"
    )

    // Punjabi Clusters
    private val punjabiCluster = listOf(
        "AP Dhillon", "Diljit Dosanjh", "Karan Aujla", "Sidhu Moose Wala", "Shubh",
        "Talwiinder", "Gurinder Gill", "Amrit Maan", "Jassie Gill", "Harrdy Sandhu",
        "Prem Dhillon", "The PropheC", "B Praak", "Jaani", "Mankirt Aulakh", "Jass Manak"
    )

    // South Indian
    private val southIndianCluster = listOf(
        "Anirudh Ravichander", "Sid Sriram", "Yuvan Shankar Raja", "Harris Jayaraj",
        "Santhosh Narayanan", "Devi Sri Prasad", "Thaman S", "Sushin Shyam",
        "G. V. Prakash Kumar", "Hesham Abdul Wahab", "A.R. Rahman"
    )

    // English Clusters
    private val englishSmoothCalmCluster = listOf(
        "Cigarettes After Sex", "Phoebe Bridgers", "Clairo", "Hozier", "Norah Jones",
        "Laufey", "Rex Orange County", "Lorde", "Bon Iver", "Sufjan Stevens", "Mac DeMarco",
        "Billie Eilish", "Joji", "Stephen Sanchez"
    )

    private val englishRomanticCluster = listOf(
        "Ed Sheeran", "Taylor Swift", "Bruno Mars", "Stephen Sanchez", "Shawn Mendes",
        "Adele", "James Arthur", "Dan + Shay", "John Legend", "Calum Scott", "Lewis Capaldi"
    )

    private val englishLofiCluster = listOf(
        "Lofi Girl", "ChilledCow", "Powfu", "Kupla", "Idealism", "Jinsang",
        "Shiloh Dynasty", "Potsu", "Tomppabeats", "Saib", "Elijah Who"
    )

    private val westernHipHopCluster = listOf(
        "Kendrick Lamar", "Drake", "J. Cole", "Travis Scott", "21 Savage", "Future",
        "Metro Boomin", "Gunna", "Lil Baby", "A\$AP Rocky", "Playboi Carti", "Lil Uzi Vert",
        "Tyler, The Creator", "Kid Cudi", "Kanye West", "Don Toliver", "Central Cee", "Eminem"
    )

    private val globalEdmCluster = listOf(
        "Martin Garrix", "Avicii", "Calvin Harris", "David Guetta", "The Chainsmokers",
        "Alan Walker", "Marshmello", "Kygo", "DJ Snake", "Tiësto", "Skrillex", "Fred again..", "Zedd"
    )

    private val globalPopCluster = listOf(
        "The Weeknd", "Bruno Mars", "Dua Lipa", "Post Malone", "Harry Styles", "The Kid LAROI",
        "Charlie Puth", "Shawn Mendes", "Olivia Rodrigo", "Billie Eilish", "Sabrina Carpenter",
        "Taylor Swift", "Ariana Grande", "Justin Bieber", "Ed Sheeran"
    )

    private val kpopCluster = listOf(
        "BTS", "BLACKPINK", "Stray Kids", "NewJeans", "TWICE", "SEVENTEEN",
        "LE SSERAFIM", "ENHYPEN", "TOMORROW X TOGETHER", "Jung Kook", "Jimin", "aespa", "ITZY", "IVE"
    )

    private val latinCluster = listOf(
        "Bad Bunny", "Rauw Alejandro", "J Balvin", "Maluma", "Ozuna", "Daddy Yankee",
        "Karol G", "Rosalía", "Feid", "Myke Towers", "Anuel AA", "Bizarrap"
    )

    fun getSimilarArtists(artistName: String, seedTrack: SieloTrack? = null): List<String> {
        val lower = artistName.trim().lowercase()
        val language = if (seedTrack != null) SongClassifier.detectLanguage(seedTrack) else null
        val vibe = if (seedTrack != null) SongClassifier.detectVibe(seedTrack) else null

        // 1. Language & Vibe-specific routing
        val pool = when (language) {
            SongLanguage.HINDI -> {
                when (vibe) {
                    SongVibe.SMOOTH_CALM -> hindiSmoothCalmCluster
                    SongVibe.ROMANTIC -> hindiRomanticCluster
                    SongVibe.LOFI -> hindiLofiCluster
                    SongVibe.RAP_HIPHOP -> hindiRapCluster
                    SongVibe.ENERGETIC_PARTY -> hindiPartyCluster
                    else -> hindiRomanticCluster + hindiSmoothCalmCluster
                }
            }
            SongLanguage.PUNJABI -> punjabiCluster
            SongLanguage.SOUTH_INDIAN -> southIndianCluster
            SongLanguage.KOREAN -> kpopCluster
            SongLanguage.LATIN -> latinCluster
            SongLanguage.ENGLISH -> {
                when (vibe) {
                    SongVibe.SMOOTH_CALM -> englishSmoothCalmCluster
                    SongVibe.ROMANTIC -> englishRomanticCluster
                    SongVibe.LOFI -> englishLofiCluster
                    SongVibe.RAP_HIPHOP -> westernHipHopCluster
                    SongVibe.ENERGETIC_PARTY -> globalEdmCluster
                    else -> globalPopCluster
                }
            }
            else -> {
                when {
                    listOf("arijit", "atif", "mohit", "kk", "shreya", "jubin", "armaan", "vishal mishra", "darshan", "sonu nigam", "pritam").any { lower.contains(it) } -> hindiRomanticCluster
                    listOf("lost stories", "zaeden", "prateek kuhad", "anuv jain", "anuv", "jasleen royal", "jasleen", "local train", "when chai", "taba chake", "sanam", "lucky ali", "twin strings").any { lower.contains(it) } -> hindiSmoothCalmCluster
                    listOf("divine", "seedhe maut", "seedhe", "kr\$na", "krsna", "mc stan", "stan", "raftaar", "badshah", "emiway", "young stunners", "bella", "paradox").any { lower.contains(it) } -> hindiRapCluster
                    listOf("dhillon", "diljit", "karan aujla", "aujla", "sidhu moose", "moose", "shubh", "talwiinder").any { lower.contains(it) } -> punjabiCluster
                    listOf("anirudh", "sid sriram", "yuvan", "harris jayaraj", "santhosh narayanan", "devi sri", "thaman", "sushin").any { lower.contains(it) } -> southIndianCluster
                    listOf("bts", "blackpink", "stray kids", "newjeans", "twice", "seventeen", "le sserafim", "enhypen", "jung kook", "jimin", "aespa").any { lower.contains(it) } -> kpopCluster
                    listOf("bad bunny", "rauw", "j balvin", "maluma", "ozuna", "daddy yankee", "karol g", "rosalia", "feid").any { lower.contains(it) } -> latinCluster
                    listOf("kendrick", "drake", "j. cole", "cole", "travis", "21 savage", "savage", "future", "metro", "gunna", "rocky", "carti", "kanye", "eminem").any { lower.contains(it) } -> westernHipHopCluster
                    listOf("cigarettes", "phoebe", "clairo", "hozier", "norah jones", "laufey", "rex orange", "bon iver").any { lower.contains(it) } -> englishSmoothCalmCluster
                    listOf("garrix", "avicii", "calvin harris", "guetta", "chainsmokers", "alan walker", "marshmello", "kygo", "dj snake", "tiesto", "skrillex").any { lower.contains(it) } -> globalEdmCluster
                    else -> globalPopCluster
                }
            }
        }

        return pool
            .filterNot { it.equals(artistName, ignoreCase = true) || lower.contains(it.lowercase()) }
            .shuffled()
    }

    fun defaultFallbackTracks(seedTrack: SieloTrack? = null): List<SieloTrack> {
        val lang = if (seedTrack != null) SongClassifier.detectLanguage(seedTrack) else SongLanguage.ENGLISH
        val vibe = if (seedTrack != null) SongClassifier.detectVibe(seedTrack) else SongVibe.POP

        return when (lang) {
            SongLanguage.HINDI -> {
                when (vibe) {
                    SongVibe.SMOOTH_CALM -> listOf(
                        SieloTrack("h-lost-1", "Taaj (Acoustic)", "Lost Stories", durationText = "3:10"),
                        SieloTrack("h-lost-2", "Sunehra (Acoustic)", "Lost Stories", durationText = "3:25"),
                        SieloTrack("h-anuv-1", "Baarishein", "Anuv Jain", durationText = "3:27"),
                        SieloTrack("h-prat-2", "Kasoor", "Prateek Kuhad", durationText = "3:16"),
                        SieloTrack("h-anuv-3", "Husn", "Anuv Jain", durationText = "3:38"),
                        SieloTrack("h-loc-1", "Choo Lo", "The Local Train", durationText = "3:53"),
                        SieloTrack("h-prat-1", "cold/mess", "Prateek Kuhad", durationText = "4:12"),
                        SieloTrack("h-luck-1", "O Sanam", "Lucky Ali", durationText = "3:46"),
                        SieloTrack("h-jas-1", "Kho Gaye Hum Kahan", "Jasleen Royal & Prateek Kuhad", durationText = "4:14"),
                        SieloTrack("h-anuv-4", "Alag Aasmaan", "Anuv Jain", durationText = "3:32"),
                        SieloTrack("h-anuv-5", "Mishri", "Anuv Jain", durationText = "3:20"),
                        SieloTrack("h-zaed-1", "tere bina", "Zaeden", durationText = "3:12"),
                        SieloTrack("h-loc-2", "Aaoge Tum Kabhi", "The Local Train", durationText = "5:14"),
                        SieloTrack("h-loc-3", "Dil Mere", "The Local Train", durationText = "4:32"),
                        SieloTrack("h-osho-1", "Khoya", "Osho Jain", durationText = "3:45"),
                        SieloTrack("h-twin-1", "Dhundhta Firaan", "Twin Strings", durationText = "3:28"),
                        SieloTrack("h-adit-1", "Chaand Baaliyan", "Aditya A", durationText = "1:44"),
                        SieloTrack("h-ritv-1", "Liggi (Acoustic)", "Ritviz", durationText = "3:01")
                    )
                    SongVibe.RAP_HIPHOP -> listOf(
                        SieloTrack("h-rap-1", "Kohinoor", "DIVINE", durationText = "3:10"),
                        SieloTrack("h-rap-2", "Namastute", "Seedhe Maut", durationText = "3:02"),
                        SieloTrack("h-rap-3", "No Cap", "KR\$NA", durationText = "3:15"),
                        SieloTrack("h-rap-4", "Mirchi", "DIVINE ft. MC Altaf", durationText = "3:25"),
                        SieloTrack("h-rap-5", "Khatta Sadak", "Seedhe Maut", durationText = "3:40"),
                        SieloTrack("h-rap-6", "Afsanay", "Young Stunners", durationText = "4:10"),
                        SieloTrack("h-rap-7", "Humble Poet", "Bella", durationText = "3:30"),
                        SieloTrack("h-rap-8", "Overthink", "Bella", durationText = "3:18"),
                        SieloTrack("h-rap-9", "Gully Gang", "DIVINE", durationText = "3:00"),
                        SieloTrack("h-rap-10", "10 Pe 10", "KR\$NA ft. French Montana", durationText = "3:32")
                    )
                    SongVibe.LOFI -> listOf(
                        SieloTrack("h-lofi-1", "Tu Jane Na (Lofi)", "Atif Aslam", durationText = "3:35"),
                        SieloTrack("h-lofi-2", "Iktara (Lofi Flip)", "Kavita Seth", durationText = "3:45"),
                        SieloTrack("h-lofi-3", "Samjhawan (Lofi)", "Arijit Singh", durationText = "4:10"),
                        SieloTrack("h-lofi-4", "Baarishein (Slowed + Reverb)", "Anuv Jain", durationText = "4:00"),
                        SieloTrack("h-lofi-5", "Dooriyan (Lofi)", "Zaeden", durationText = "3:15"),
                        SieloTrack("h-lofi-6", "Faasle (Lofi)", "Aditya Rikhari", durationText = "3:20")
                    )
                    else -> listOf(
                        SieloTrack("h-rom-1", "Tum Hi Ho", "Arijit Singh", durationText = "4:22"),
                        SieloTrack("h-rom-2", "Kesariya", "Arijit Singh & Pritam", durationText = "4:28"),
                        SieloTrack("h-rom-3", "Raataan Lambiyan", "Jubin Nautiyal & Asees Kaur", durationText = "3:50"),
                        SieloTrack("h-rom-4", "Apna Bana Le", "Arijit Singh & Sachin-Jigar", durationText = "4:21"),
                        SieloTrack("h-rom-5", "Hawayein", "Arijit Singh", durationText = "4:50"),
                        SieloTrack("h-rom-6", "Dil Diyan Gallan", "Atif Aslam", durationText = "4:20"),
                        SieloTrack("h-rom-7", "Pehla Pyaar", "Armaan Malik", durationText = "4:32"),
                        SieloTrack("h-rom-8", "Tera Ban Jaunga", "Akhil Sachdeva & Tulsi Kumar", durationText = "3:56"),
                        SieloTrack("h-rom-9", "Agar Tum Saath Ho", "Arijit Singh & Alka Yagnik", durationText = "5:41"),
                        SieloTrack("h-rom-10", "Kabira", "Arijit Singh & Harshdeep Kaur", durationText = "3:43"),
                        SieloTrack("h-rom-11", "Channa Mereya", "Arijit Singh", durationText = "4:49"),
                        SieloTrack("h-rom-12", "Subhanallah", "Sreerama Chandra & Shilpa Rao", durationText = "4:09")
                    )
                }
            }
            SongLanguage.PUNJABI -> listOf(
                SieloTrack("p-1", "Excuses", "AP Dhillon & Gurinder Gill", durationText = "2:56"),
                SieloTrack("p-2", "Lover", "Diljit Dosanjh", durationText = "3:08"),
                SieloTrack("p-3", "Winning Speech", "Karan Aujla", durationText = "3:24"),
                SieloTrack("p-4", "295", "Sidhu Moose Wala", durationText = "4:30"),
                SieloTrack("p-5", "Cheques", "Shubh", durationText = "3:03"),
                SieloTrack("p-6", "Dhuaan", "Talwiinder", durationText = "3:12"),
                SieloTrack("p-7", "Born to Shine", "Diljit Dosanjh", durationText = "3:33"),
                SieloTrack("p-8", "Brown Munde", "AP Dhillon & Gurinder Gill", durationText = "4:07"),
                SieloTrack("p-9", "Softly", "Karan Aujla", durationText = "2:35"),
                SieloTrack("p-10", "One Love", "Shubh", durationText = "2:39"),
                SieloTrack("p-11", "Khayaal", "Talwiinder", durationText = "3:14"),
                SieloTrack("p-12", "With You", "AP Dhillon", durationText = "2:34")
            )
            SongLanguage.SOUTH_INDIAN -> listOf(
                SieloTrack("s-1", "Arabic Kuthu", "Anirudh Ravichander", durationText = "4:40"),
                SieloTrack("s-2", "Marakkuma Nenjam", "A.R. Rahman", durationText = "4:15"),
                SieloTrack("s-3", "Kadhaippoma", "Sid Sriram", durationText = "4:32"),
                SieloTrack("s-4", "Naatu Naatu", "Rahul Sipligunj & Kaala Bhairava", durationText = "3:34"),
                SieloTrack("s-5", "Hukum", "Anirudh Ravichander", durationText = "3:27"),
                SieloTrack("s-6", "Illuminati", "Sushin Shyam", durationText = "3:10"),
                SieloTrack("s-7", "Inkem Inkem Inkem Kaavaale", "Sid Sriram", durationText = "4:27"),
                SieloTrack("s-8", "Rowdy Baby", "Dhanush & Dhee", durationText = "4:41")
            )
            SongLanguage.ENGLISH -> {
                when (vibe) {
                    SongVibe.SMOOTH_CALM -> listOf(
                        SieloTrack("e-calm-1", "Apocalypse", "Cigarettes After Sex", durationText = "4:50"),
                        SieloTrack("e-calm-2", "From the Start", "Laufey", durationText = "2:49"),
                        SieloTrack("e-calm-3", "Sofia", "Clairo", durationText = "3:08"),
                        SieloTrack("e-calm-4", "Until I Found You", "Stephen Sanchez", durationText = "2:57"),
                        SieloTrack("e-calm-5", "Motion Sickness", "Phoebe Bridgers", durationText = "3:49"),
                        SieloTrack("e-calm-6", "Take Me to Church", "Hozier", durationText = "4:01"),
                        SieloTrack("e-calm-7", "Don't Know Why", "Norah Jones", durationText = "3:05")
                    )
                    SongVibe.ROMANTIC -> listOf(
                        SieloTrack("e-rom-1", "Perfect", "Ed Sheeran", durationText = "4:23"),
                        SieloTrack("e-rom-2", "Lover", "Taylor Swift", durationText = "3:41"),
                        SieloTrack("e-rom-3", "Just the Way You Are", "Bruno Mars", durationText = "3:40"),
                        SieloTrack("e-rom-4", "All of Me", "John Legend", durationText = "4:29"),
                        SieloTrack("e-rom-5", "Say You Won't Let Go", "James Arthur", durationText = "3:31")
                    )
                    SongVibe.RAP_HIPHOP -> listOf(
                        SieloTrack("e-rap-1", "HUMBLE.", "Kendrick Lamar", durationText = "2:57"),
                        SieloTrack("e-rap-2", "God's Plan", "Drake", durationText = "3:18"),
                        SieloTrack("e-rap-3", "SICKO MODE", "Travis Scott", durationText = "5:12"),
                        SieloTrack("e-rap-4", "No Role Modelz", "J. Cole", durationText = "4:52"),
                        SieloTrack("e-rap-5", "Lose Yourself", "Eminem", durationText = "5:26")
                    )
                    SongVibe.LOFI -> listOf(
                        SieloTrack("e-lofi-1", "death bed (coffee for your head)", "Powfu ft. beabadoobee", durationText = "2:53"),
                        SieloTrack("e-lofi-2", "Get You The Moon", "Kina ft. Snøw", durationText = "2:59"),
                        SieloTrack("e-lofi-3", "Snowman", "WYS", durationText = "3:15")
                    )
                    else -> listOf(
                        SieloTrack("fW-Mxsnu", "Blinding Lights", "The Weeknd", durationText = "3:20"),
                        SieloTrack("TcDP-KUl", "Starboy", "The Weeknd ft. Daft Punk", durationText = "3:50"),
                        SieloTrack("9q41tYDn", "Die For You", "The Weeknd", durationText = "4:20"),
                        SieloTrack("tvxo4Jm0", "Save Your Tears", "The Weeknd", durationText = "3:35"),
                        SieloTrack("3IoDK8qI", "Levitating", "Dua Lipa", durationText = "3:23"),
                        SieloTrack("wwSCc15h", "Shape of You", "Ed Sheeran", durationText = "3:53"),
                        SieloTrack("wLxoOff5", "Uptown Funk", "Mark Ronson ft. Bruno Mars", durationText = "4:30")
                    )
                }
            }
            else -> listOf(
                SieloTrack("fW-Mxsnu", "Blinding Lights", "The Weeknd", durationText = "3:20"),
                SieloTrack("TcDP-KUl", "Starboy", "The Weeknd ft. Daft Punk", durationText = "3:50"),
                SieloTrack("3IoDK8qI", "Levitating", "Dua Lipa", durationText = "3:23")
            )
        }
    }
}
