import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "healthy"

def test_ingest_events():
    payload = {
        "events": [
            {
                "eventId": "test-ev-1",
                "userId": "test_user_123",
                "trackId": "video_abc",
                "eventType": "play_completed",
                "timestampMs": 1700000000000,
                "playbackPositionMs": 240000,
                "durationMs": 240000,
                "trackTitle": "Tum Hi Ho",
                "trackArtist": "Arijit Singh"
            }
        ]
    }
    response = client.post(
        "/v1/events",
        json=payload,
        headers={"Authorization": "Bearer test_user_123"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["accepted"] >= 1

def test_recommendations_endpoint():
    payload = {
        "userId": "test_user_123",
        "seedTrackId": "video_abc",
        "seedTitle": "Tum Hi Ho",
        "seedArtist": "Arijit Singh",
        "limit": 10
    }
    response = client.post(
        "/v1/recommendations",
        json=payload,
        headers={"Authorization": "Bearer test_user_123"}
    )
    assert response.status_code == 200
    data = response.json()
    assert "modelVersion" in data
    assert "recommendations" in data

def test_similar_tracks_endpoint():
    response = client.get("/v1/recommendations/similar/video_abc?limit=5")
    assert response.status_code == 200
    data = response.json()
    assert data["seedTrackId"] == "video_abc"
    assert "similarTracks" in data

def test_model_status_endpoint():
    response = client.get("/v1/models/status")
    assert response.status_code == 200
    data = response.json()
    assert "activeModelVersion" in data
