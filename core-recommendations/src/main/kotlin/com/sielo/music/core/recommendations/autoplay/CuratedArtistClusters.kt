package com.sielo.music.core.recommendations.autoplay

import com.sielo.music.core.network.models.SieloTrack

object CuratedArtistClusters {

    private val indianEdmCluster = listOf(
        "Lost Stories", "Nucleya", "Zaeden", "Ritviz", "KSHMR", "Sickflip",
        "Anyasa", "Dualist Inquiry", "DJ Chetas", "MojoJojo", "Sartek", "Zephyrtone"
    )

    private val indianIndieCluster = listOf(
        "Prateek Kuhad", "Anuv Jain", "Jasleen Royal", "The Local Train", "When Chai Met Toast",
        "Taba Chake", "Sanam", "Osho Jain", "The Dream Note", "Twin Strings", "Aditya A"
    )

    private val bollywoodCluster = listOf(
        "Arijit Singh", "Atif Aslam", "Mohit Chauhan", "KK", "Shreya Ghoshal", "Jubin Nautiyal",
        "Armaan Malik", "Vishal Mishra", "Darshan Raval", "Sonu Nigam", "Pritam", "Sachin-Jigar",
        "Shankar Mahadevan", "Sunidhi Chauhan", "Javed Ali", "Akhil Sachdeva", "Amit Trivedi", "Neeti Mohan"
    )

    private val desiHipHopCluster = listOf(
        "DIVINE", "Seedhe Maut", "KR\$NA", "MC Stan", "Raftaar", "Badshah", "Emiway Bantai",
        "Ikka", "Bella", "Paradox", "Fotty Seven", "EPR", "Dino James", "Raga", "Karma",
        "Young Stunners", "Talha Anjum"
    )

    private val punjabiCluster = listOf(
        "AP Dhillon", "Diljit Dosanjh", "Karan Aujla", "Sidhu Moose Wala", "Shubh",
        "Talwiinder", "Gurinder Gill", "Amrit Maan", "Jassie Gill", "Harrdy Sandhu", "Prem Dhillon", "The PropheC"
    )

    private val southIndianCluster = listOf(
        "Anirudh Ravichander", "Sid Sriram", "Yuvan Shankar Raja", "Harris Jayaraj",
        "Santhosh Narayanan", "Devi Sri Prasad", "Thaman S", "Sushin Shyam", "G. V. Prakash Kumar", "Hesham Abdul Wahab"
    )

    private val westernHipHopCluster = listOf(
        "Kendrick Lamar", "Drake", "J. Cole", "Travis Scott", "21 Savage", "Future",
        "Metro Boomin", "Gunna", "Lil Baby", "A\$AP Rocky", "Playboi Carti", "Lil Uzi Vert",
        "Tyler, The Creator", "Kid Cudi", "Kanye West", "Don Toliver", "Central Cee"
    )

    private val globalRnbCluster = listOf(
        "The Weeknd", "SZA", "Frank Ocean", "Daniel Caesar", "Summer Walker", "Jhené Aiko",
        "Brent Faiyaz", "Giveon", "H.E.R.", "Steve Lacy", "Kali Uchis", "Kehlani",
        "Bryson Tiller", "6LACK", "PARTYNEXTDOOR", "Snoh Aalegra", "Jorja Smith"
    )

    private val globalIndieCluster = listOf(
        "Arctic Monkeys", "The Neighbourhood", "Lana Del Rey", "Cigarettes After Sex",
        "Hozier", "Mac DeMarco", "Phoebe Bridgers", "Clairo", "Rex Orange County", "Wallows", "girl in red", "The 1975"
    )

    private val globalEdmCluster = listOf(
        "Martin Garrix", "Avicii", "Calvin Harris", "David Guetta", "The Chainsmokers",
        "Alan Walker", "Marshmello", "Kygo", "DJ Snake", "Tiësto", "Skrillex", "Fred again..", "Zedd", "Galantis", "Alesso"
    )

    private val globalRockCluster = listOf(
        "Imagine Dragons", "Coldplay", "Linkin Park", "Twenty One Pilots", "Fall Out Boy",
        "Panic! At The Disco", "Green Day", "Foo Fighters", "Red Hot Chili Peppers", "Radiohead",
        "Muse", "Bring Me The Horizon", "The Killers", "OneRepublic", "Paramore"
    )

    private val kpopCluster = listOf(
        "BTS", "BLACKPINK", "Stray Kids", "NewJeans", "TWICE", "SEVENTEEN",
        "LE SSERAFIM", "ENHYPEN", "TOMORROW X TOGETHER", "Jung Kook", "Jimin", "aespa", "ITZY", "IVE"
    )

    private val latinCluster = listOf(
        "Bad Bunny", "Rauw Alejandro", "J Balvin", "Maluma", "Ozuna", "Daddy Yankee",
        "Karol G", "Rosalía", "Feid", "Myke Towers", "Anuel AA", "Bizarrap"
    )

    private val globalPopCluster = listOf(
        "The Weeknd", "Bruno Mars", "Dua Lipa", "Post Malone", "Harry Styles", "The Kid LAROI",
        "Charlie Puth", "Shawn Mendes", "Olivia Rodrigo", "Billie Eilish", "Sabrina Carpenter",
        "Taylor Swift", "Ariana Grande", "Justin Bieber", "Ed Sheeran"
    )

    fun getSimilarArtists(artistName: String): List<String> {
        val lower = artistName.trim().lowercase()
        val pool = when {
            listOf("lost stories", "nucleya", "zaeden", "ritviz", "kshmr", "sickflip", "anyasa", "chetas").any { lower.contains(it) } -> indianEdmCluster
            listOf("prateek kuhad", "anuv jain", "anuv", "jasleen royal", "jasleen", "local train", "when chai", "taba chake", "sanam").any { lower.contains(it) } -> indianIndieCluster
            listOf("arijit", "atif", "mohit", "kk", "shreya", "jubin", "armaan", "vishal mishra", "darshan", "sonu nigam", "pritam").any { lower.contains(it) } -> bollywoodCluster
            listOf("divine", "seedhe maut", "seedhe", "kr\$na", "krsna", "mc stan", "stan", "raftaar", "badshah", "emiway", "young stunners").any { lower.contains(it) } -> desiHipHopCluster
            listOf("dhillon", "diljit", "karan aujla", "aujla", "sidhu moose", "moose", "shubh", "talwiinder").any { lower.contains(it) } -> punjabiCluster
            listOf("anirudh", "sid sriram", "yuvan", "harris jayaraj", "santhosh narayanan", "devi sri", "thaman", "sushin").any { lower.contains(it) } -> southIndianCluster
            listOf("bts", "blackpink", "stray kids", "newjeans", "twice", "seventeen", "le sserafim", "enhypen", "jung kook", "jimin", "aespa").any { lower.contains(it) } -> kpopCluster
            listOf("bad bunny", "rauw", "j balvin", "maluma", "ozuna", "daddy yankee", "karol g", "rosalia", "feid").any { lower.contains(it) } -> latinCluster
            listOf("kendrick", "drake", "j. cole", "cole", "travis", "21 savage", "savage", "future", "metro", "gunna", "rocky", "carti", "kanye").any { lower.contains(it) } -> westernHipHopCluster
            listOf("weeknd", "sza", "frank ocean", "caesar", "summer walker", "jhene", "giveon", "steve lacy", "kali uchis", "bryson").any { lower.contains(it) } -> globalRnbCluster
            listOf("arctic", "neighbourhood", "lana", "cigarettes", "hozier", "demarco", "phoebe", "clairo", "rex orange", "wallows").any { lower.contains(it) } -> globalIndieCluster
            listOf("garrix", "avicii", "calvin harris", "guetta", "chainsmokers", "alan walker", "marshmello", "kygo", "dj snake", "tiesto", "skrillex", "fred again").any { lower.contains(it) } -> globalEdmCluster
            listOf("imagine dragons", "linkin", "coldplay", "twenty one", "green day", "foo fighters", "chili peppers", "radiohead", "muse", "killers", "onerepublic").any { lower.contains(it) } -> globalRockCluster
            listOf("bruno", "dua lipa", "post malone", "styles", "laroi", "puth", "mendes", "olivia", "billie", "sabrina", "taylor", "ariana", "bieber", "sheeran").any { lower.contains(it) } -> globalPopCluster
            else -> globalPopCluster
        }

        return pool
            .filterNot { it.equals(artistName, ignoreCase = true) || lower.contains(it.lowercase()) }
            .shuffled()
    }

    fun defaultFallbackTracks(): List<SieloTrack> = listOf(
        SieloTrack("fW-Mxsnu", "Blinding Lights", "The Weeknd", durationText = "3:20"),
        SieloTrack("TcDP-KUl", "Starboy", "The Weeknd ft. Daft Punk", durationText = "3:50"),
        SieloTrack("9q41tYDn", "Die For You", "The Weeknd", durationText = "4:20"),
        SieloTrack("tvxo4Jm0", "Save Your Tears", "The Weeknd", durationText = "3:35"),
        SieloTrack("3IoDK8qI", "Levitating", "Dua Lipa", durationText = "3:23"),
        SieloTrack("wwSCc15h", "Shape of You", "Ed Sheeran", durationText = "3:53"),
        SieloTrack("EWoDxjbu", "New Rules", "Dua Lipa", durationText = "3:29"),
        SieloTrack("wLxoOff5", "Uptown Funk", "Mark Ronson ft. Bruno Mars", durationText = "4:30"),
        SieloTrack("kd8JSDbB", "Stay", "The Kid LAROI & Justin Bieber", durationText = "2:21")
    )
}
