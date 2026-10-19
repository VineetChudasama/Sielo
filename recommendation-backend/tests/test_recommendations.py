import pytest
from app.data.interaction_matrix import calculate_event_weight, build_interaction_matrix
from app.recommendation.collaborative import CollaborativeRecommender
from app.recommendation.content_based import ContentBasedRecommender
from app.recommendation.personal_taste import PersonalTasteScorer
from app.recommendation.audio_intelligence import audio_provider
from app.recommendation.hybrid_ranker import HybridRanker
from app.evaluation.evaluator import ModelEvaluator

def test_event_weights():
    # Liked is positive
    assert calculate_event_weight("liked", 180000, 180000) == 2.5
    # Completed is positive
    assert calculate_event_weight("play_completed", 180000, 180000) == 1.2
    # Early skip (<15%) is negative
    assert calculate_event_weight("skipped", 180000, 10000) == -1.0
    # Late skip (>40%) is not penalized heavily
    assert calculate_event_weight("skipped", 180000, 150000) == 0.1

def test_interaction_matrix_and_cf():
    # Synthetic listening events
    events = [
        {"eventId": "e1", "userId": "u1", "trackId": "t1", "eventType": "play_completed", "durationMs": 200000, "playbackPositionMs": 200000, "timestampMs": 1000000},
        {"eventId": "e2", "userId": "u1", "trackId": "t2", "eventType": "liked", "durationMs": 200000, "playbackPositionMs": 200000, "timestampMs": 1000000},
        {"eventId": "e3", "userId": "u2", "trackId": "t1", "eventType": "play_completed", "durationMs": 200000, "playbackPositionMs": 200000, "timestampMs": 1000000},
        {"eventId": "e4", "userId": "u2", "trackId": "t2", "eventType": "play_completed", "durationMs": 200000, "playbackPositionMs": 200000, "timestampMs": 1000000},
        {"eventId": "e5", "userId": "u2", "trackId": "t3", "eventType": "liked", "durationMs": 200000, "playbackPositionMs": 200000, "timestampMs": 1000000},
        {"eventId": "e6", "userId": "u3", "trackId": "t2", "eventType": "play_completed", "durationMs": 200000, "playbackPositionMs": 200000, "timestampMs": 1000000},
        {"eventId": "e7", "userId": "u3", "trackId": "t3", "eventType": "play_completed", "durationMs": 200000, "playbackPositionMs": 200000, "timestampMs": 1000000},
        {"eventId": "e8", "userId": "u3", "trackId": "t4", "eventType": "play_completed", "durationMs": 200000, "playbackPositionMs": 200000, "timestampMs": 1000000},
        {"eventId": "e9", "userId": "u4", "trackId": "t3", "eventType": "play_completed", "durationMs": 200000, "playbackPositionMs": 200000, "timestampMs": 1000000},
        {"eventId": "e10", "userId": "u4", "trackId": "t4", "eventType": "liked", "durationMs": 200000, "playbackPositionMs": 200000, "timestampMs": 1000000},
        {"eventId": "e11", "userId": "u4", "trackId": "t5", "eventType": "play_completed", "durationMs": 200000, "playbackPositionMs": 200000, "timestampMs": 1000000},
    ]

    mat, u_map, t_map, idx_u, idx_t = build_interaction_matrix(events)
    assert mat.shape[0] == 4  # 4 users
    assert mat.shape[1] == 5  # 5 tracks

    cf = CollaborativeRecommender()
    cf.fit(mat, u_map, t_map, idx_u, idx_t)
    assert cf.is_trained is True

    # User 1 listened to t1, t2 -> should get recommendations including t3
    recs = cf.recommend_for_user("u1", limit=3)
    rec_ids = [t for t, _ in recs]
    assert "t3" in rec_ids

    # Similar tracks to t1 -> t2 should have high similarity
    sims = cf.get_similar_tracks("t1", limit=2)
    assert len(sims) > 0
    assert sims[0][0] == "t2"

def test_content_based_similarity():
    cb = ContentBasedRecommender()
    track_a = {"id": "1", "title": "Tum Hi Ho", "artist": "Arijit Singh", "album": "Aashiqui 2", "durationSeconds": 260}
    track_b = {"id": "2", "title": "Chahun Main Ya Naa", "artist": "Arijit Singh", "album": "Aashiqui 2", "durationSeconds": 270}
    track_c = {"id": "3", "title": "Shape of You", "artist": "Ed Sheeran", "album": "Divide", "durationSeconds": 233}

    sim_ab = cb.compute_similarity(track_a, track_b)
    sim_ac = cb.compute_similarity(track_a, track_c)

    assert sim_ab > 0.6  # Same artist and album
    assert sim_ac < 0.2  # Unrelated songs

def test_personal_taste_scorer():
    scorer = PersonalTasteScorer()
    profile = {
        "favoriteArtists": ["Arijit Singh", "The Weeknd"],
        "favoriteGenres": ["Bollywood", "R&B"],
        "artistTasteWeights": {"Arijit Singh": 1.0}
    }
    fav_track = {"id": "t1", "artist": "Arijit Singh", "genre": "Bollywood"}
    unknown_track = {"id": "t2", "artist": "Unknown Artist", "genre": "Classical"}

    score_fav = scorer.score_track_for_user(profile, fav_track)
    score_un = scorer.score_track_for_user(profile, unknown_track)

    assert score_fav > score_un
    # Recent skip penalty
    score_skipped = scorer.score_track_for_user(profile, fav_track, recent_skips=["t1"])
    assert score_skipped < 0.1

def test_audio_feature_provider_no_fabrication():
    # Must never invent BPM or key values
    features = audio_provider.get_features("test_track", {"title": "Test Song"})
    assert features.available is False
    assert features.bpm is None
    assert features.musicalKey is None
    assert features.confidence == 0.0

def test_hybrid_ranking():
    cf = CollaborativeRecommender()
    cb = ContentBasedRecommender()
    pt = PersonalTasteScorer()
    ranker = HybridRanker(cf, cb, pt)

    candidates = [
        {"id": "t1", "title": "Song 1", "artist": "Artist A", "viewCount": 5000000},
        {"id": "t2", "title": "Song 2", "artist": "Artist A", "viewCount": 2000000},
        {"id": "t3", "title": "Song 3", "artist": "Artist B", "viewCount": 1000000},
    ]

    recs = ranker.rank(
        user_id="user_1",
        user_profile={"favoriteArtists": ["Artist B"]},
        seed_track=None,
        candidate_tracks=candidates,
        limit=5
    )

    assert len(recs) == 3
    # Artist B should be prioritized because it matches user profile
    assert recs[0].artist == "Artist B"

def test_evaluation_metrics():
    user_recs = {"u1": ["t1", "t2", "t3"], "u2": ["t4", "t5", "t6"]}
    ground_truth = {"u1": {"t1", "t2"}, "u2": {"t4", "t7"}}
    catalog = {"t1", "t2", "t3", "t4", "t5", "t6", "t7"}
    track_artists = {"t1": "A1", "t2": "A2", "t3": "A3", "t4": "B1", "t5": "B2", "t6": "B3"}

    metrics = ModelEvaluator.evaluate(user_recs, ground_truth, catalog, track_artists, k=3)
    assert metrics.precision_at_k > 0.0
    assert metrics.recall_at_k > 0.0
    assert metrics.catalog_coverage > 0.0
