import time
import logging
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query
from app.auth.firebase_auth import get_current_user
from app.schemas.recommendations import (
    RecommendationRequest,
    RecommendationResponse,
    SimilarTracksResponse,
    RecommendationItem
)
from app.training.model_store import model_store
from app.data.firestore_client import firestore_client
from app.recommendation.content_based import ContentBasedRecommender
from app.recommendation.personal_taste import PersonalTasteScorer
from app.recommendation.hybrid_ranker import HybridRanker

logger = logging.getLogger("sielo.api.recommendations")
router = APIRouter(prefix="/v1/recommendations", tags=["Recommendations"])

# Singletons for serving
content_recommender = ContentBasedRecommender()
personal_scorer = PersonalTasteScorer()

@router.post("", response_model=RecommendationResponse)
async def get_recommendations(
    request: RecommendationRequest,
    auth_user_id: Optional[str] = Depends(get_current_user)
):
    """
    Primary hybrid recommendation endpoint.
    Combines Collaborative Filtering, User Preferences, Content Similarity, and Discovery.
    """
    user_id = request.userId or auth_user_id
    active_cf = model_store.load_active_model()
    ranker = HybridRanker(active_cf, content_recommender, personal_scorer)

    # 1. Fetch user profile if user is known
    user_profile = None
    if user_id:
        user_profile = firestore_client.get_user_profile(user_id)

    # 2. Build seed track dictionary if provided
    seed_track = None
    if request.seedTrackId:
        seed_track = firestore_client.get_track_metadata(request.seedTrackId) or {
            "id": request.seedTrackId,
            "title": request.seedTitle or "",
            "artist": request.seedArtist or ""
        }

    # 3. Gather candidate tracks from catalog / recent history
    candidate_tracks = []
    # Seed track itself if known
    if seed_track:
        candidate_tracks.append(seed_track)

    # Recent tracks from events
    recent_events = firestore_client.fetch_recent_events(limit=500)
    seen_ids = set()
    for ev in recent_events:
        t_id = ev.get("trackId")
        if t_id and t_id not in seen_ids:
            seen_ids.add(t_id)
            candidate_tracks.append({
                "id": t_id,
                "title": ev.get("trackTitle", ""),
                "artist": ev.get("trackArtist", ""),
                "album": ev.get("trackAlbum", "")
            })

    # Update content catalog for similarity lookups
    content_recommender.update_catalog(candidate_tracks)

    # 4. Hybrid Ranking
    recommendations = ranker.rank(
        user_id=user_id,
        user_profile=user_profile,
        seed_track=seed_track,
        candidate_tracks=candidate_tracks,
        excluded_track_ids=request.excludedTrackIds,
        limit=request.limit,
        diversity_factor=request.diversityFactor
    )

    is_fallback = not active_cf.is_trained

    return RecommendationResponse(
        modelVersion=active_cf.model_version,
        isFallback=is_fallback,
        generatedAtMs=int(time.time() * 1000),
        recommendations=recommendations,
        metadata={
            "userId": user_id or "anonymous",
            "candidateCount": str(len(candidate_tracks)),
            "cfModelActive": str(active_cf.is_trained)
        }
    )

@router.get("/similar/{track_id}", response_model=SimilarTracksResponse)
async def get_similar_tracks(
    track_id: str,
    limit: int = Query(default=10, ge=1, le=50)
):
    """
    Returns tracks musically and collaboratively similar to a given seed track.
    """
    active_cf = model_store.load_active_model()
    seed_meta = firestore_client.get_track_metadata(track_id) or {"id": track_id}

    items = []

    # 1. Try collaborative similarity if model is trained
    if active_cf.is_trained and track_id in active_cf.track_to_idx:
        cf_sims = active_cf.get_similar_tracks(track_id, limit=limit)
        for t_id, score in cf_sims:
            meta = firestore_client.get_track_metadata(t_id) or {"id": t_id}
            items.append(RecommendationItem(
                trackId=t_id,
                title=meta.get("title"),
                artist=meta.get("artist"),
                album=meta.get("album"),
                thumbnailUrl=meta.get("thumbnailUrl"),
                score=round(score, 4),
                reason="Frequently co-listened with this track",
                source="collaborative"
            ))

    # 2. Backfill with content-based metadata similarity
    if len(items) < limit:
        needed = limit - len(items)
        content_sims = content_recommender.find_similar_to_track(
            seed_meta, limit=needed, excluded_track_ids=[it.trackId for it in items] + [track_id]
        )
        for t_id, score in content_sims:
            meta = firestore_client.get_track_metadata(t_id) or {"id": t_id}
            items.append(RecommendationItem(
                trackId=t_id,
                title=meta.get("title"),
                artist=meta.get("artist"),
                album=meta.get("album"),
                thumbnailUrl=meta.get("thumbnailUrl"),
                score=round(score, 4),
                reason="Similar musical and artist profile",
                source="content_based"
            ))

    return SimilarTracksResponse(
        seedTrackId=track_id,
        modelVersion=active_cf.model_version,
        similarTracks=items
    )
