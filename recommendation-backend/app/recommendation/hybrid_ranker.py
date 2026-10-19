import logging
from typing import List, Dict, Any, Optional
from app.configuration.config import settings
from app.schemas.recommendations import RecommendationItem
from app.recommendation.collaborative import CollaborativeRecommender
from app.recommendation.content_based import ContentBasedRecommender
from app.recommendation.personal_taste import PersonalTasteScorer
from app.recommendation.audio_intelligence import audio_provider

logger = logging.getLogger("sielo.hybrid")

class HybridRanker:
    """
    Combines Collaborative, Personal Taste, Content-Based, and Discovery signals
    into a unified ranking with diversity controls and dynamic weight re-normalization.
    """
    def __init__(
        self,
        collaborative: CollaborativeRecommender,
        content_based: ContentBasedRecommender,
        personal_taste: PersonalTasteScorer
    ):
        self.collaborative = collaborative
        self.content_based = content_based
        self.personal_taste = personal_taste

    def rank(
        self,
        user_id: Optional[str],
        user_profile: Optional[Dict[str, Any]],
        seed_track: Optional[Dict[str, Any]],
        candidate_tracks: List[Dict[str, Any]],
        excluded_track_ids: Optional[List[str]] = None,
        limit: int = 20,
        diversity_factor: float = 0.3
    ) -> List[RecommendationItem]:
        if not candidate_tracks:
            return []

        excluded = set(excluded_track_ids or [])
        seed_id = (seed_track.get("id") or seed_track.get("trackId")) if seed_track else None
        if seed_id:
            excluded.add(seed_id)

        # 1. Gather Collaborative Scores
        cf_dict = {}
        if user_id and self.collaborative.is_trained:
            cf_list = self.collaborative.recommend_for_user(user_id, limit=limit * 2, excluded_track_ids=list(excluded))
            cf_dict = {t_id: s for t_id, s in cf_list}

        # 2. Determine active weights and re-normalize dynamically
        w_cf = settings.WEIGHT_COLLABORATIVE if cf_dict else 0.0
        w_personal = settings.WEIGHT_PERSONAL_PREFERENCE if user_profile else 0.10
        w_content = settings.WEIGHT_CONTENT_SIMILARITY if seed_track else 0.15
        w_disc = settings.WEIGHT_DISCOVERY
        w_audio = 0.0  # Safe default

        total_weight = w_cf + w_personal + w_content + w_disc + w_audio
        if total_weight <= 0.0:
            total_weight = 1.0

        w_cf /= total_weight
        w_personal /= total_weight
        w_content /= total_weight
        w_disc /= total_weight

        scored_candidates = []

        for track in candidate_tracks:
            t_id = track.get("id") or track.get("trackId")
            if not t_id or t_id in excluded:
                continue

            # Collaborative Score [0.0 - 1.0]
            score_cf = cf_dict.get(t_id, 0.0)

            # Personal Taste Score [0.0 - 1.0]
            score_personal = self.personal_taste.score_track_for_user(user_profile, track)

            # Content Similarity Score [0.0 - 1.0]
            score_content = 0.0
            if seed_track:
                score_content = self.content_based.compute_similarity(seed_track, track)

            # Discovery Score (inversely proportional to popularity or based on seed divergence)
            # High views = familiar, lower views = exploration/discovery
            views = float(track.get("viewCount", 100000))
            score_discovery = max(0.1, min(1.0, 1.0 - (views / 500000000.0)))

            # Composite Score
            final_score = (
                score_cf * w_cf +
                score_personal * w_personal +
                score_content * w_content +
                score_discovery * w_disc
            )

            # Determine dominant recommendation reason
            reason = "Recommended for your music taste"
            source = "hybrid"
            if score_cf > 0.4 and score_cf >= max(score_personal, score_content):
                reason = "Listeners with similar taste enjoy this"
                source = "collaborative"
            elif score_content > 0.5:
                seed_artist = (seed_track.get("artist") or seed_track.get("trackArtist") or "this track") if seed_track else "this"
                reason = f"Similar musical vibe to {seed_artist}"
                source = "content_based"
            elif score_personal > 0.6:
                reason = "Matches your favorite artists & genre preferences"
                source = "personal_taste"
            elif not self.collaborative.is_trained:
                source = "fallback"
                reason = "Curated acoustic match"

            scored_candidates.append({
                "track": track,
                "score": float(final_score),
                "reason": reason,
                "source": source
            })

        # Sort descending by score
        scored_candidates.sort(key=lambda x: x["score"], reverse=True)

        # 3. Apply Diversity & Artist Spacing (avoid clustering more than 2 tracks from same artist)
        selected: List[RecommendationItem] = []
        recent_artists: List[str] = []

        for item in scored_candidates:
            track = item["track"]
            t_id = track.get("id") or track.get("trackId")
            artist = (track.get("artist") or track.get("trackArtist") or "").strip().lower()

            # Penalty if same artist was placed in last 2 slots
            if artist and artist in recent_artists[-2:]:
                if len(scored_candidates) > limit * 2:
                    continue  # Skip for now to favor artist diversity

            selected.append(RecommendationItem(
                trackId=t_id,
                title=track.get("title") or track.get("trackTitle"),
                artist=track.get("artist") or track.get("trackArtist"),
                album=track.get("album") or track.get("trackAlbum"),
                thumbnailUrl=track.get("thumbnailUrl"),
                score=round(item["score"], 4),
                reason=item["reason"],
                source=item["source"]
            ))

            recent_artists.append(artist)
            if len(selected) >= limit:
                break

        return selected
