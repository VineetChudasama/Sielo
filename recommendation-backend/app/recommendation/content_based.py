import re
from typing import List, Dict, Any, Tuple, Optional
import math

class ContentBasedRecommender:
    """
    Computes metadata-driven similarity across songs.
    Useful for cold-start users, new songs with 0 plays, or single-artist radio queues.
    """
    def __init__(self):
        self._tracks_catalog: Dict[str, Dict[str, Any]] = {}

    def update_catalog(self, tracks: List[Dict[str, Any]]):
        for t in tracks:
            t_id = t.get("id") or t.get("trackId")
            if t_id:
                self._tracks_catalog[t_id] = t

    def get_track(self, track_id: str) -> Optional[Dict[str, Any]]:
        return self._tracks_catalog.get(track_id)

    @staticmethod
    def _clean_tokens(text: str) -> set:
        if not text:
            return set()
        cleaned = re.sub(r'[\(\)\[\],.\-!?:;"]', ' ', text.lower())
        words = cleaned.split()
        stop_words = {"the", "a", "an", "and", "or", "feat", "ft", "in", "of", "to", "official", "video", "audio"}
        return {w for w in words if w not in stop_words and len(w) > 1}

    def compute_similarity(self, track_a: Dict[str, Any], track_b: Dict[str, Any]) -> float:
        """
        Calculates similarity score [0.0, 1.0] between two tracks.
        """
        if not track_a or not track_b:
            return 0.0

        score = 0.0

        # 1. Exact Artist Match (highest signal)
        artist_a = (track_a.get("artist") or track_a.get("trackArtist") or "").strip().lower()
        artist_b = (track_b.get("artist") or track_b.get("trackArtist") or "").strip().lower()

        if artist_a and artist_b:
            if artist_a == artist_b:
                score += 0.50
            elif artist_a in artist_b or artist_b in artist_a:
                score += 0.35

        # 2. Album Match
        album_a = (track_a.get("album") or track_a.get("trackAlbum") or "").strip().lower()
        album_b = (track_b.get("album") or track_b.get("trackAlbum") or "").strip().lower()
        if album_a and album_b and album_a == album_b:
            score += 0.20

        # 3. Language Match
        lang_a = (track_a.get("language") or "").strip().lower()
        lang_b = (track_b.get("language") or "").strip().lower()
        if lang_a and lang_b and lang_a == lang_b:
            score += 0.15

        # 4. Duration Similarity (songs of similar length within ~30s)
        dur_a = float(track_a.get("durationSeconds") or (track_a.get("durationMs", 0) / 1000.0) or 0)
        dur_b = float(track_b.get("durationSeconds") or (track_b.get("durationMs", 0) / 1000.0) or 0)
        if dur_a > 0 and dur_b > 0:
            diff_ratio = abs(dur_a - dur_b) / max(dur_a, dur_b)
            if diff_ratio < 0.15:
                score += 0.10
            elif diff_ratio < 0.30:
                score += 0.05

        # 5. Title Token Overlap (Jaccard on non-stopword tokens)
        tokens_a = self._clean_tokens(track_a.get("title") or track_a.get("trackTitle") or "")
        tokens_b = self._clean_tokens(track_b.get("title") or track_b.get("trackTitle") or "")
        if tokens_a and tokens_b:
            intersection = tokens_a.intersection(tokens_b)
            union = tokens_a.union(tokens_b)
            jaccard = len(intersection) / float(len(union))
            # Don't reward exact title duplicate (might be a remix/cover)
            if jaccard < 0.9:
                score += min(0.15, jaccard * 0.25)

        return min(1.0, score)

    def find_similar_to_track(
        self,
        seed_track: Dict[str, Any],
        limit: int = 20,
        excluded_track_ids: Optional[List[str]] = None
    ) -> List[Tuple[str, float]]:
        excluded = set(excluded_track_ids or [])
        seed_id = seed_track.get("id") or seed_track.get("trackId")
        if seed_id:
            excluded.add(seed_id)

        scored_tracks = []
        for t_id, candidate in self._tracks_catalog.items():
            if t_id in excluded:
                continue
            sim = self.compute_similarity(seed_track, candidate)
            if sim > 0.15:
                scored_tracks.append((t_id, sim))

        scored_tracks.sort(key=lambda x: x[1], reverse=True)
        return scored_tracks[:limit]
