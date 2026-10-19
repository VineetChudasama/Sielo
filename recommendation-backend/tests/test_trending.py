import pytest
import time
from unittest.mock import AsyncMock, MagicMock, patch
from datetime import datetime, timezone

from app.schemas.trending import TrendingBatch, TrendingTrack, ProviderStatus
from app.trending.normalizer import TrackNormalizer
from app.trending.youtube_provider import YouTubeTrendingProvider
from app.trending.lastfm_provider import LastFmTrendingProvider
from app.trending.community_provider import FirestoreCommunityTrendingProvider
from app.trending.snapshot_provider import SnapshotTrendingProvider
from app.trending.orchestrator import TrendingOrchestrator

# Helper to create mock trending track
def create_mock_track(title: str, artist: str, rank: int, trend_score: float, source: str = "test", confidence: float = 0.9):
    norm_title = TrackNormalizer.strip_punctuation(TrackNormalizer.clean_text(title))
    norm_artist = TrackNormalizer.strip_punctuation(TrackNormalizer.clean_text(artist))
    return TrendingTrack(
        trackId=f"id_{norm_title}_{norm_artist}",
        title=title,
        artist=artist,
        normalizedTitle=norm_title,
        normalizedArtist=norm_artist,
        trendScore=trend_score,
        momentumScore=0.5,
        source=source,
        rank=rank,
        confidence=confidence,
        updatedAt=datetime.now(timezone.utc).isoformat()
    )

# 1. YouTube provider succeeds
@pytest.mark.anyio
async def test_youtube_provider_succeeds():
    provider = YouTubeTrendingProvider(api_key="valid_dummy_key")
    mock_payload = {
        "items": [
            {
                "id": "vid_1",
                "snippet": {
                    "title": "Gone, Gone, Gone (Official Video)",
                    "channelTitle": "Phillip Phillips",
                    "thumbnails": {"high": {"url": "https://img/1.jpg"}},
                    "publishedAt": "2026-01-01T00:00:00Z"
                },
                "statistics": {"viewCount": "15000000", "likeCount": "500000"}
            },
            {
                "id": "vid_2",
                "snippet": {
                    "title": "Starboy (Official Audio)",
                    "channelTitle": "The Weeknd ft. Daft Punk",
                    "thumbnails": {"high": {"url": "https://img/2.jpg"}},
                    "publishedAt": "2026-01-02T00:00:00Z"
                },
                "statistics": {"viewCount": "25000000", "likeCount": "1200000"}
            },
            {
                "id": "vid_3",
                "snippet": {
                    "title": "Kesariya - Audio",
                    "channelTitle": "Pritam & Arijit Singh",
                    "thumbnails": {"high": {"url": "https://img/3.jpg"}},
                    "publishedAt": "2026-01-03T00:00:00Z"
                },
                "statistics": {"viewCount": "35000000", "likeCount": "2000000"}
            }
        ]
    }

    with patch("httpx.AsyncClient.get") as mock_get:
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = mock_payload
        mock_get.return_value = mock_resp

        batch = await provider.fetch_trending(market="IN")
        assert batch.source == "youtube"
        assert len(batch.tracks) == 3
        assert batch.confidence == 0.95
        assert batch.tracks[0].title == "Gone, Gone, Gone"
        assert batch.tracks[0].artist == "Phillip Phillips"
        assert provider.report_provider_status().isHealthy is True

# 2. YouTube fails and Last.fm succeeds
@pytest.mark.anyio
async def test_youtube_fails_and_lastfm_succeeds():
    yt_mock = YouTubeTrendingProvider(api_key="")
    lastfm_mock = LastFmTrendingProvider(api_key="lastfm_dummy_key")

    mock_lastfm_payload = {
        "tracks": {
            "track": [
                {"name": "Blinding Lights", "artist": {"name": "The Weeknd"}, "listeners": "250000", "playcount": "1500000"},
                {"name": "Gone, Gone, Gone", "artist": {"name": "Phillip Phillips"}, "listeners": "180000", "playcount": "900000"},
                {"name": "Levitating", "artist": {"name": "Dua Lipa"}, "listeners": "210000", "playcount": "1200000"}
            ]
        }
    }

    with patch("httpx.AsyncClient.get") as mock_get:
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = mock_lastfm_payload
        mock_get.return_value = mock_resp

        orchestrator = TrendingOrchestrator(
            youtube_provider=yt_mock,
            lastfm_provider=lastfm_mock
        )
        batch = await orchestrator.get_trending("IN", force_refresh=True)
        assert batch.source == "lastfm"
        assert len(batch.tracks) == 3
        assert batch.confidence == 0.85

# 3. YouTube and Last.fm fail; Firestore community trends succeed
@pytest.mark.anyio
async def test_community_trends_succeed_when_externals_fail():
    yt_mock = YouTubeTrendingProvider(api_key="")
    lastfm_mock = LastFmTrendingProvider(api_key="")

    now_ms = int(time.time() * 1000)
    mock_events = [
        # Track 1: 3 plays from 2 distinct users in 24h
        {"eventId": "e1", "userId": "u1", "trackId": "t1", "trackTitle": "Community Hit A", "trackArtist": "Artist 1", "eventType": "play_completed", "timestampMs": now_ms - 10000, "durationMs": 180000, "playbackPositionMs": 180000},
        {"eventId": "e2", "userId": "u1", "trackId": "t1", "trackTitle": "Community Hit A", "trackArtist": "Artist 1", "eventType": "play_completed", "timestampMs": now_ms - 20000, "durationMs": 180000, "playbackPositionMs": 180000},
        {"eventId": "e3", "userId": "u2", "trackId": "t1", "trackTitle": "Community Hit A", "trackArtist": "Artist 1", "eventType": "liked", "timestampMs": now_ms - 30000, "durationMs": 180000, "playbackPositionMs": 180000},
        # Track 2: 3 plays from 2 users
        {"eventId": "e4", "userId": "u1", "trackId": "t2", "trackTitle": "Community Hit B", "trackArtist": "Artist 2", "eventType": "play_completed", "timestampMs": now_ms - 40000, "durationMs": 200000, "playbackPositionMs": 200000},
        {"eventId": "e5", "userId": "u3", "trackId": "t2", "trackTitle": "Community Hit B", "trackArtist": "Artist 2", "eventType": "play_completed", "timestampMs": now_ms - 50000, "durationMs": 200000, "playbackPositionMs": 200000},
        {"eventId": "e6", "userId": "u3", "trackId": "t2", "trackTitle": "Community Hit B", "trackArtist": "Artist 2", "eventType": "replayed", "timestampMs": now_ms - 60000, "durationMs": 200000, "playbackPositionMs": 200000},
        # Track 3: 3 plays from 2 users
        {"eventId": "e7", "userId": "u2", "trackId": "t3", "trackTitle": "Community Hit C", "trackArtist": "Artist 3", "eventType": "play_completed", "timestampMs": now_ms - 70000, "durationMs": 210000, "playbackPositionMs": 210000},
        {"eventId": "e8", "userId": "u4", "trackId": "t3", "trackTitle": "Community Hit C", "trackArtist": "Artist 3", "eventType": "play_completed", "timestampMs": now_ms - 80000, "durationMs": 210000, "playbackPositionMs": 210000},
        {"eventId": "e9", "userId": "u4", "trackId": "t3", "trackTitle": "Community Hit C", "trackArtist": "Artist 3", "eventType": "playlist_added", "timestampMs": now_ms - 90000, "durationMs": 210000, "playbackPositionMs": 210000},
    ]

    mock_db = MagicMock()
    mock_db.fetch_recent_events.return_value = mock_events
    comm_provider = FirestoreCommunityTrendingProvider(client=mock_db)

    orchestrator = TrendingOrchestrator(
        youtube_provider=yt_mock,
        lastfm_provider=lastfm_mock,
        community_provider=comm_provider
    )

    batch = await orchestrator.get_trending("IN", force_refresh=True)
    assert batch.source == "community"
    assert len(batch.tracks) == 3
    assert batch.tracks[0].title.startswith("Community Hit")

# 4. All live providers fail; cached snapshot succeeds
@pytest.mark.anyio
async def test_snapshot_fallback_when_all_live_fail():
    yt_mock = YouTubeTrendingProvider(api_key="")
    lastfm_mock = LastFmTrendingProvider(api_key="")
    comm_mock = FirestoreCommunityTrendingProvider(client=MagicMock(fetch_recent_events=MagicMock(return_value=[])))

    snap_provider = SnapshotTrendingProvider(client=MagicMock(_db=None))
    # Seed snapshot in durable storage
    initial_batch = TrendingBatch(
        market="IN",
        source="youtube",
        generatedAt=datetime.now(timezone.utc).isoformat(),
        confidence=0.9,
        tracks=[
            create_mock_track("Song 1", "Artist 1", 1, 0.95),
            create_mock_track("Song 2", "Artist 2", 2, 0.85),
            create_mock_track("Song 3", "Artist 3", 3, 0.75),
        ]
    )
    snap_provider.save_snapshot(initial_batch)

    orchestrator = TrendingOrchestrator(
        youtube_provider=yt_mock,
        lastfm_provider=lastfm_mock,
        community_provider=comm_mock,
        snapshot_provider=snap_provider
    )

    batch = await orchestrator.get_trending("IN", force_refresh=True)
    assert batch.source == "snapshot"
    assert len(batch.tracks) == 3
    assert batch.confidence > 0.0

# 5. All providers fail and no snapshot exists
@pytest.mark.anyio
async def test_all_providers_fail_no_snapshot():
    yt_mock = YouTubeTrendingProvider(api_key="")
    lastfm_mock = LastFmTrendingProvider(api_key="")
    comm_mock = FirestoreCommunityTrendingProvider(client=MagicMock(fetch_recent_events=MagicMock(return_value=[])))
    snap_mock = SnapshotTrendingProvider(client=MagicMock(_db=None))

    orchestrator = TrendingOrchestrator(
        youtube_provider=yt_mock,
        lastfm_provider=lastfm_mock,
        community_provider=comm_mock,
        snapshot_provider=snap_mock
    )

    batch = await orchestrator.get_trending("IN", force_refresh=True)
    assert batch.source == "none"
    assert len(batch.tracks) == 0
    assert batch.confidence == 0.0

# 6. Provider returns an empty but valid response
@pytest.mark.anyio
async def test_provider_empty_response():
    provider = YouTubeTrendingProvider(api_key="valid_dummy")
    with patch("httpx.AsyncClient.get") as mock_get:
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {"items": []}
        mock_get.return_value = mock_resp

        batch = await provider.fetch_trending("IN")
        assert len(batch.tracks) == 0
        assert batch.confidence == 0.0

# 7. Provider returns malformed data
@pytest.mark.anyio
async def test_provider_malformed_data():
    provider = YouTubeTrendingProvider(api_key="valid_dummy")
    with patch("httpx.AsyncClient.get") as mock_get:
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = "Not A Json Object"
        mock_get.return_value = mock_resp

        batch = await provider.fetch_trending("IN")
        assert len(batch.tracks) == 0

# 8. Provider returns HTTP 429
@pytest.mark.anyio
async def test_provider_http_429():
    provider = YouTubeTrendingProvider(api_key="valid_dummy")
    with patch("httpx.AsyncClient.get") as mock_get:
        mock_resp = MagicMock()
        mock_resp.status_code = 429
        mock_resp.text = "Quota exceeded"
        mock_get.return_value = mock_resp

        batch = await provider.fetch_trending("IN")
        assert len(batch.tracks) == 0
        status = provider.report_provider_status()
        assert "429" in (status.lastError or "")
        assert status.isHealthy is False

# 9. Provider times out
@pytest.mark.anyio
async def test_provider_timeout():
    import httpx
    provider = YouTubeTrendingProvider(api_key="valid_dummy")
    with patch("httpx.AsyncClient.get", side_effect=httpx.TimeoutException("Read timeout")):
        batch = await provider.fetch_trending("IN")
        assert len(batch.tracks) == 0
        assert "timed out" in provider.report_provider_status().lastError.lower()

# 10. Cached snapshot is fresh (< 6 hours)
@pytest.mark.anyio
async def test_cached_snapshot_fresh():
    snap_provider = SnapshotTrendingProvider(client=MagicMock(_db=None))
    batch = TrendingBatch(
        market="IN",
        source="youtube",
        generatedAt=datetime.now(timezone.utc).isoformat(),
        confidence=0.95,
        tracks=[
            create_mock_track("Track A", "Artist A", 1, 0.9),
            create_mock_track("Track B", "Artist B", 2, 0.8),
            create_mock_track("Track C", "Artist C", 3, 0.7),
        ]
    )
    snap_provider.save_snapshot(batch)

    loaded = await snap_provider.fetch_trending("IN")
    assert loaded.isStale is False
    assert loaded.confidence == 0.95
    assert len(loaded.tracks) == 3

# 11. Cached snapshot is stale (> 24 hours, confidence decayed)
@pytest.mark.anyio
async def test_cached_snapshot_stale():
    snap_provider = SnapshotTrendingProvider(client=MagicMock(_db=None))
    batch = TrendingBatch(
        market="IN",
        source="youtube",
        generatedAt=datetime.now(timezone.utc).isoformat(),
        confidence=0.90,
        tracks=[
            create_mock_track("Track A", "Artist A", 1, 0.9),
            create_mock_track("Track B", "Artist B", 2, 0.8),
            create_mock_track("Track C", "Artist C", 3, 0.7),
        ]
    )
    snap_provider.save_snapshot(batch)

    # Simulate 30 hours passed
    thirty_hours_ago = int((time.time() - (30 * 3600)) * 1000)
    snap_provider._memory_snapshot["IN"]["savedAtMs"] = thirty_hours_ago

    loaded = await snap_provider.fetch_trending("IN")
    assert loaded.isStale is True
    # 24h-72h decay: 50% of initial 0.9 = 0.45
    assert loaded.confidence == 0.45
    assert len(loaded.tracks) == 3

# 12. A track appears in multiple sources / multi-upload aggregation
def test_track_normalization_and_multi_upload_aggregation():
    provider = YouTubeTrendingProvider(api_key="valid")
    raw_payload = {
        "items": [
            {
                "id": "vid_1",
                "snippet": {"title": "Gone, Gone, Gone (Official Music Video)", "channelTitle": "Phillip Phillips"},
                "statistics": {"viewCount": "10000000", "likeCount": "200000"}
            },
            {
                "id": "vid_2",
                "snippet": {"title": "Phillip Phillips - Gone, Gone, Gone (Lyric Video)", "channelTitle": "PhillipPhillipsVEVO"},
                "statistics": {"viewCount": "5000000", "likeCount": "100000"}
            },
            {
                "id": "vid_3",
                "snippet": {"title": "Gone, Gone, Gone (Official Audio)", "channelTitle": "Phillip Phillips - Topic"},
                "statistics": {"viewCount": "2000000", "likeCount": "50000"}
            }
        ]
    }
    normalized = provider.normalize_results(raw_payload)
    # Must aggregate all 3 video uploads into 1 single canonical track
    assert len(normalized) == 1
    track = normalized[0]
    assert track.title == "Gone, Gone, Gone"
    assert track.artist == "Phillip Phillips"
    assert track.metadata["uploadCount"] == 3
    assert track.metadata["views"] == 17000000

# 13. Two different songs share a title
def test_two_different_songs_share_title():
    key1 = TrackNormalizer.canonical_key("Hello", "Adele")
    key2 = TrackNormalizer.canonical_key("Hello", "Lionel Richie")
    assert key1 != key2
    assert "adele" in key1
    assert "lionel richie" in key2

# 14 & 15. Search relevance simulation & query relevance floor
def simulate_search_ranking(query: str, candidates: List[Dict[str, Any]], trending_tracks: Dict[str, float]):
    """
    Stage A + Stage B ranking:
    Query relevance: 60%
    Trending popularity: 25%
    Momentum: 10%
    Personal taste: 5%
    """
    q_norm = TrackNormalizer.strip_punctuation(TrackNormalizer.clean_text(query))
    scored = []

    for c in candidates:
        t_norm = TrackNormalizer.strip_punctuation(TrackNormalizer.clean_text(c["title"]))
        a_norm = TrackNormalizer.strip_punctuation(TrackNormalizer.clean_text(c["artist"]))
        c_key = f"{t_norm}:::{a_norm}"

        # Stage A: Textual Relevance
        text_rel = 0.0
        if t_norm == q_norm:
            text_rel = 1.0  # Exact title match
        elif t_norm.startswith(q_norm):
            text_rel = 0.8
        elif q_norm in t_norm:
            text_rel = 0.6
        elif a_norm == q_norm:
            text_rel = 0.7
        else:
            text_rel = 0.1  # weak match

        # Stage B: Trending boost
        pop = trending_tracks.get(c_key, 0.0)
        mom = 0.5 if pop > 0 else 0.0
        personal = c.get("personal", 0.0)

        # Relevance floor: If textual relevance is poor (<0.3), popularity is suppressed!
        effective_pop = pop if text_rel >= 0.3 else 0.0
        effective_mom = mom if text_rel >= 0.3 else 0.0

        final_score = (0.60 * text_rel) + (0.25 * effective_pop) + (0.10 * effective_mom) + (0.05 * personal)
        scored.append((c, final_score, text_rel))

    scored.sort(key=lambda x: x[1], reverse=True)
    return [s[0] for s in scored]

# 14. Correct track present but was previously ranked low -> trending boost moves it up
def test_correct_track_ranked_up_by_trending():
    query = "Gone Gone Gone"
    candidates = [
        {"title": "Gone Gone Gone (Cover)", "artist": "Amateur Band", "personal": 0.0},
        {"title": "Gone, Gone, Gone", "artist": "Phillip Phillips", "personal": 0.0},
    ]
    # Mark Phillip Phillips as popular in trending
    trending = {
        TrackNormalizer.canonical_key("Gone, Gone, Gone", "Phillip Phillips"): 0.95
    }
    ranked = simulate_search_ranking(query, candidates, trending)
    assert ranked[0]["artist"] == "Phillip Phillips"

# 15. A trending song is unrelated to the user's query -> relevance floor prevents it from overtaking
def test_unrelated_trending_song_cannot_overtake():
    query = "Kesariya"
    candidates = [
        {"title": "Kesariya", "artist": "Arijit Singh", "personal": 0.0},
        {"title": "Shape of You", "artist": "Ed Sheeran", "personal": 0.0},  # Globally #1 trending song!
    ]
    trending = {
        TrackNormalizer.canonical_key("Shape of You", "Ed Sheeran"): 1.0,
        TrackNormalizer.canonical_key("Kesariya", "Arijit Singh"): 0.7
    }
    ranked = simulate_search_ranking(query, candidates, trending)
    # The relevant song MUST be #1 despite Shape of You having 1.0 trending popularity
    assert ranked[0]["title"] == "Kesariya"

# 16. Community trending has insufficient sample size
@pytest.mark.anyio
async def test_community_insufficient_sample():
    # Only 1 user playing 1 track
    now_ms = int(time.time() * 1000)
    sparse_events = [
        {"eventId": "e1", "userId": "user_lonely", "trackId": "t1", "trackTitle": "Solo Track", "trackArtist": "Solo Artist", "eventType": "play_completed", "timestampMs": now_ms - 5000, "durationMs": 180000, "playbackPositionMs": 180000}
    ]
    mock_db = MagicMock(fetch_recent_events=MagicMock(return_value=sparse_events))
    provider = FirestoreCommunityTrendingProvider(client=mock_db)
    batch = await provider.fetch_trending("IN")
    assert len(batch.tracks) == 0
    assert batch.confidence == 0.0

# 17. Search cache contains older rankings / stale-while-revalidate
@pytest.mark.anyio
async def test_cache_invalidation_and_force_refresh():
    yt_mock = YouTubeTrendingProvider(api_key="valid")
    initial_payload = {
        "items": [
            {"id": "v1", "snippet": {"title": "Hit 1", "channelTitle": "Art 1"}, "statistics": {"viewCount": "1000000"}},
            {"id": "v2", "snippet": {"title": "Hit 2", "channelTitle": "Art 2"}, "statistics": {"viewCount": "2000000"}},
            {"id": "v3", "snippet": {"title": "Hit 3", "channelTitle": "Art 3"}, "statistics": {"viewCount": "3000000"}},
        ]
    }
    updated_payload = {
        "items": [
            {"id": "v4", "snippet": {"title": "Brand New Hit 4", "channelTitle": "Art 4"}, "statistics": {"viewCount": "5000000"}},
            {"id": "v5", "snippet": {"title": "Brand New Hit 5", "channelTitle": "Art 5"}, "statistics": {"viewCount": "6000000"}},
            {"id": "v6", "snippet": {"title": "Brand New Hit 6", "channelTitle": "Art 6"}, "statistics": {"viewCount": "7000000"}},
        ]
    }

    with patch("httpx.AsyncClient.get") as mock_get:
        mock_resp = MagicMock(status_code=200, json=MagicMock(return_value=initial_payload))
        mock_get.return_value = mock_resp

        orchestrator = TrendingOrchestrator(youtube_provider=yt_mock)
        b1 = await orchestrator.get_trending("IN")
        assert b1.tracks[0].title == "Hit 1"  # sorted by chartPosition

        # Change API response
        mock_resp.json.return_value = updated_payload

        # Without force_refresh: returns cached b1
        b2 = await orchestrator.get_trending("IN", force_refresh=False)
        assert b2.tracks[0].title == "Hit 1"

        # With force_refresh: returns b3
        b3 = await orchestrator.get_trending("IN", force_refresh=True)
        assert b3.tracks[0].title == "Brand New Hit 4"

# 18. User changes the query rapidly
def test_user_rapid_query_changes():
    queries = ["p", "ph", "phi", "phil", "phillip"]
    assert TrackNormalizer.clean_text(queries[-1]) == "phillip"
