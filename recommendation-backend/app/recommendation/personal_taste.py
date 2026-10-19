from typing import Dict, Any, List, Optional

class PersonalTasteScorer:
    """
    Evaluates tracks against a specific user's listening profile.
    Leverages favorite artists, genre weights, and short-term negative signals (recent skips).
    """
    def __init__(self):
        pass

    def score_track_for_user(
        self,
        user_profile: Optional[Dict[str, Any]],
        track: Dict[str, Any],
        recent_skips: Optional[List[str]] = None
    ) -> float:
        if not user_profile or not track:
            return 0.3  # Neutral baseline for anonymous/cold-start

        score = 0.3
        t_artist = (track.get("artist") or track.get("trackArtist") or "").strip().lower()
        t_id = track.get("id") or track.get("trackId")

        # 1. Negative Signal: Track recently skipped
        if recent_skips and t_id in recent_skips:
            return 0.05

        # 2. Favorite Artists Match
        fav_artists = [a.strip().lower() for a in user_profile.get("favoriteArtists", []) if isinstance(a, str)]
        if t_artist and t_artist in fav_artists:
            score += 0.40

        # 3. Artist Taste Weights
        artist_weights = user_profile.get("artistTasteWeights", {})
        if isinstance(artist_weights, dict) and t_artist in artist_weights:
            try:
                w = float(artist_weights[t_artist])
                score += min(0.25, max(-0.2, w * 0.25))
            except (ValueError, TypeError):
                pass

        # 4. Favorite Genres Match
        t_genre = (track.get("genre") or "").strip().lower()
        fav_genres = [g.strip().lower() for g in user_profile.get("favoriteGenres", []) if isinstance(g, str)]
        if t_genre and t_genre in fav_genres:
            score += 0.20

        return min(1.0, max(0.0, score))
