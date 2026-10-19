import time
import math
import logging
from typing import Any, List, Dict, Optional, Set
from datetime import datetime, timezone

from app.configuration.config import settings
from app.schemas.trending import TrendingBatch, TrendingTrack, ProviderStatus
from app.trending.provider_base import TrendingProvider
from app.trending.normalizer import TrackNormalizer
from app.data.firestore_client import firestore_client

logger = logging.getLogger("sielo.trending.community")

class FirestoreCommunityTrendingProvider(TrendingProvider):
    """
    Fallback 2: Firestore Community Trending Provider.
    Aggregates genuine Sielo listening events over 24-hour and 7-day windows,
    enforcing minimum listener sample thresholds, user-play caps, and momentum growth.
    """

    QUALIFIED_EVENT_TYPES = {
        "play_completed",
        "replayed",
        "liked",
        "playlist_added"
    }

    def __init__(self, client = None):
        self._firestore = client or firestore_client
        self._last_attempt_at: Optional[str] = None
        self._last_success_at: Optional[str] = None
        self._last_latency_ms: int = 0
        self._last_error: Optional[str] = None
        self._usable_count: int = 0
        self._is_healthy: bool = True

    @property
    def name(self) -> str:
        return "community"

    async def fetch_trending(self, market: str = "IN") -> TrendingBatch:
        self._last_attempt_at = datetime.now(timezone.utc).isoformat()
        start_time = time.monotonic()

        try:
            now_ms = int(time.time() * 1000)
            window_24h_ms = 24 * 60 * 60 * 1000
            window_48h_ms = 48 * 60 * 60 * 1000
            window_7d_ms = 7 * 24 * 60 * 60 * 1000

            # 1. Fetch events up to 48 hours ago (or 7 days if sparse)
            cutoff_ms = now_ms - window_48h_ms
            events = self._firestore.fetch_recent_events(limit=10000, since_ms=cutoff_ms)

            # If 24h events are sparse (< 10 events), expand query to 7 days
            if len(events) < 10:
                logger.info(f"[{self.name}] 48h event volume low ({len(events)} events), checking 7-day window...")
                cutoff_ms = now_ms - window_7d_ms
                events = self._firestore.fetch_recent_events(limit=10000, since_ms=cutoff_ms)

            self._last_latency_ms = int((time.monotonic() - start_time) * 1000)

            tracks = self.normalize_results(events, market=market)

            min_required = settings.MIN_COMMUNITY_TRENDING_PLAYS
            if len(tracks) < min_required:
                self._last_error = f"Insufficient community dataset ({len(tracks)} tracks qualified, minimum {min_required} required)"
                self._is_healthy = True
                self._usable_count = len(tracks)
                logger.info(f"[{self.name}] {self._last_error}")
                return self._empty_batch(market)

            self._is_healthy = True
            self._last_error = None
            self._last_success_at = datetime.now(timezone.utc).isoformat()
            self._usable_count = len(tracks)
            logger.info(f"[{self.name}] Successfully aggregated {len(tracks)} community trending tracks in {self._last_latency_ms}ms")

            return TrendingBatch(
                market=market,
                source=self.name,
                generatedAt=datetime.now(timezone.utc).isoformat(),
                isStale=False,
                confidence=0.75,
                tracks=tracks
            )

        except Exception as e:
            self._last_latency_ms = int((time.monotonic() - start_time) * 1000)
            self._last_error = f"Failed to aggregate community trends: {str(e)}"
            self._is_healthy = False
            logger.error(f"[{self.name}] {self._last_error}", exc_info=True)
            return self._empty_batch(market)

    def normalize_results(self, raw_data: Any, market: str = "IN") -> List[TrendingTrack]:
        """
        Processes listening events:
        - Filters out skipped or accidental playback (<15s)
        - Caps maximum plays per user per track to 5 (prevents domination)
        - Enforces minimum unique listener threshold (default 2)
        - Calculates momentum growth vs previous time window
        """
        if not isinstance(raw_data, list):
            return []

        now_ms = int(time.time() * 1000)
        now_iso = datetime.now(timezone.utc).isoformat()
        current_window_ms = 24 * 60 * 60 * 1000
        prev_window_ms = 48 * 60 * 60 * 1000

        # Track aggregation map: canonical_key -> { current_plays, prev_plays, user_plays, track_info }
        track_map: Dict[str, Dict[str, Any]] = {}

        for ev in raw_data:
            if not isinstance(ev, dict):
                continue

            event_type = str(ev.get("eventType", "")).lower()
            track_id = ev.get("trackId")
            user_id = ev.get("userId", "anonymous")
            ts = ev.get("timestampMs", 0)
            pos_ms = ev.get("playbackPositionMs", 0)
            dur_ms = ev.get("durationMs", 0)

            # Play-counting rule:
            # Must be a qualified completion, replay, like, or progress >= 30 seconds
            is_qualified = (
                event_type in self.QUALIFIED_EVENT_TYPES or
                (event_type == "playback_progress" and pos_ms >= 30_000)
            )

            # Exclude explicit skips or short accidental plays
            if event_type == "skipped" or (dur_ms > 0 and pos_ms < 15_000):
                is_qualified = False

            if not is_qualified or not track_id:
                continue

            title = ev.get("trackTitle") or "Unknown Title"
            artist = ev.get("trackArtist") or "Unknown Artist"
            clean_title = TrackNormalizer.clean_title(title)
            c_key = TrackNormalizer.canonical_key(clean_title, artist)

            if c_key not in track_map:
                track_map[c_key] = {
                    "trackId": track_id,
                    "title": clean_title,
                    "artist": artist,
                    "current_user_plays": {},  # user_id -> count
                    "prev_user_plays": {},     # user_id -> count
                    "listeners": set()
                }

            entry = track_map[c_key]
            age_ms = now_ms - ts

            if age_ms <= current_window_ms:
                # Current 24h window
                entry["current_user_plays"][user_id] = entry["current_user_plays"].get(user_id, 0) + 1
                entry["listeners"].add(user_id)
            elif age_ms <= prev_window_ms:
                # Previous comparable window (24h to 48h)
                entry["prev_user_plays"][user_id] = entry["prev_user_plays"].get(user_id, 0) + 1

        min_listeners = settings.MIN_COMMUNITY_TRENDING_LISTENERS
        min_plays = settings.MIN_COMMUNITY_TRENDING_PLAYS
        user_play_cap = 5

        qualified_candidates = []

        for c_key, data in track_map.items():
            # Apply per-user play cap
            capped_current_plays = sum(min(count, user_play_cap) for count in data["current_user_plays"].values())
            capped_prev_plays = sum(min(count, user_play_cap) for count in data["prev_user_plays"].values())
            unique_listeners = len(data["listeners"])

            # Enforce sample thresholds
            if unique_listeners < min_listeners or capped_current_plays < min_plays:
                continue

            # Momentum growth calculation
            growth = (capped_current_plays - capped_prev_plays) / max(capped_prev_plays, 1)
            momentum = round(min(1.0, max(0.0, 0.5 + (growth * 0.25))), 4)

            # Combined community score: 60% play volume, 40% listener diversity
            score = (capped_current_plays * 0.6) + (unique_listeners * 1.5 * 0.4)

            qualified_candidates.append({
                "trackId": data["trackId"],
                "title": data["title"],
                "artist": data["artist"],
                "currentPlays": capped_current_plays,
                "prevPlays": capped_prev_plays,
                "uniqueListeners": unique_listeners,
                "momentumScore": momentum,
                "compositeScore": score
            })

        # Sort by composite score
        qualified_candidates.sort(key=lambda x: x["compositeScore"], reverse=True)

        total_items = max(len(qualified_candidates), 1)
        result: List[TrendingTrack] = []

        for rank, entry in enumerate(qualified_candidates, start=1):
            rank_factor = 1.0 - ((rank - 1) / total_items)
            play_factor = min(1.0, math.log10(max(entry["currentPlays"], 1) + 1) / 2.0)
            trend_score = round((0.7 * rank_factor) + (0.3 * play_factor), 4)

            result.append(
                TrendingTrack(
                    trackId=entry["trackId"],
                    title=entry["title"],
                    artist=entry["artist"],
                    normalizedTitle=TrackNormalizer.strip_punctuation(TrackNormalizer.clean_text(entry["title"])),
                    normalizedArtist=TrackNormalizer.strip_punctuation(TrackNormalizer.clean_text(entry["artist"])),
                    trendScore=trend_score,
                    momentumScore=entry["momentumScore"],
                    source=self.name,
                    rank=rank,
                    confidence=0.75,
                    updatedAt=now_iso,
                    thumbnailUrl=None,
                    metadata={
                        "playCountInWindow": entry["currentPlays"],
                        "previousWindowPlayCount": entry["prevPlays"],
                        "uniqueListenerCount": entry["uniqueListeners"]
                    }
                )
            )

        return result

    def report_provider_status(self) -> ProviderStatus:
        return ProviderStatus(
            providerName=self.name,
            isConfigured=True,
            isHealthy=self._is_healthy,
            lastAttemptAt=self._last_attempt_at,
            lastSuccessAt=self._last_success_at,
            lastLatencyMs=self._last_latency_ms,
            lastError=self._last_error,
            usableCandidateCount=self._usable_count
        )

    def _empty_batch(self, market: str) -> TrendingBatch:
        return TrendingBatch(
            market=market,
            source=self.name,
            generatedAt=datetime.now(timezone.utc).isoformat(),
            isStale=False,
            confidence=0.0,
            tracks=[]
        )
