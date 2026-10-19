import time
import math
import logging
from typing import Any, List, Dict, Optional
from datetime import datetime, timezone
import httpx

from app.configuration.config import settings
from app.schemas.trending import TrendingBatch, TrendingTrack, ProviderStatus
from app.trending.provider_base import TrendingProvider
from app.trending.normalizer import TrackNormalizer

logger = logging.getLogger("sielo.trending.youtube")

class YouTubeTrendingProvider(TrendingProvider):
    """
    Primary Trending Source: Official YouTube Data API v3.
    Fetches the most popular music videos for the specified regionCode
    using videoCategoryId=10 (Music) and aggregates multi-upload canonical tracks.
    """

    YOUTUBE_VIDEOS_ENDPOINT = "https://www.googleapis.com/youtube/v3/videos"
    MUSIC_CATEGORY_ID = "10"  # Global YouTube Video Category ID for Music

    def __init__(self, api_key: Optional[str] = None):
        self._api_key = api_key or settings.YOUTUBE_API_KEY
        self._last_attempt_at: Optional[str] = None
        self._last_success_at: Optional[str] = None
        self._last_latency_ms: int = 0
        self._last_error: Optional[str] = None
        self._usable_count: int = 0
        self._is_healthy: bool = True

    @property
    def name(self) -> str:
        return "youtube"

    async def fetch_trending(self, market: str = "IN") -> TrendingBatch:
        self._last_attempt_at = datetime.now(timezone.utc).isoformat()
        start_time = time.monotonic()

        # Check API key configuration
        if not self._api_key:
            self._last_error = "YOUTUBE_API_KEY not configured in environment"
            self._is_healthy = False
            self._usable_count = 0
            logger.warning(f"[{self.name}] {self._last_error}")
            return TrendingBatch(
                market=market,
                source=self.name,
                generatedAt=datetime.now(timezone.utc).isoformat(),
                isStale=False,
                confidence=0.0,
                tracks=[]
            )

        params = {
            "part": "snippet,contentDetails,statistics",
            "chart": "mostPopular",
            "regionCode": market.upper(),
            "videoCategoryId": self.MUSIC_CATEGORY_ID,
            "maxResults": "50",
            "key": self._api_key
        }

        try:
            async with httpx.AsyncClient(timeout=8.0) as client:
                response = await client.get(self.YOUTUBE_VIDEOS_ENDPOINT, params=params)
                self._last_latency_ms = int((time.monotonic() - start_time) * 1000)

                if response.status_code == 429:
                    self._last_error = "HTTP 429: YouTube API quota exceeded or rate limited"
                    self._is_healthy = False
                    logger.warning(f"[{self.name}] {self._last_error}")
                    return self._empty_batch(market)

                if response.status_code != 200:
                    self._last_error = f"HTTP {response.status_code}: {response.text[:200]}"
                    self._is_healthy = False
                    logger.warning(f"[{self.name}] {self._last_error}")
                    return self._empty_batch(market)

                data = response.json()
                tracks = self.normalize_results(data, market=market)

                if not tracks:
                    self._last_error = "Zero usable music candidates after normalization"
                    self._is_healthy = True
                    self._usable_count = 0
                    logger.info(f"[{self.name}] {self._last_error}")
                    return self._empty_batch(market)

                self._is_healthy = True
                self._last_error = None
                self._last_success_at = datetime.now(timezone.utc).isoformat()
                self._usable_count = len(tracks)
                logger.info(f"[{self.name}] Successfully fetched {len(tracks)} trending tracks for market={market} in {self._last_latency_ms}ms")

                return TrendingBatch(
                    market=market,
                    source=self.name,
                    generatedAt=datetime.now(timezone.utc).isoformat(),
                    isStale=False,
                    confidence=0.95,
                    tracks=tracks
                )

        except httpx.TimeoutException:
            self._last_latency_ms = int((time.monotonic() - start_time) * 1000)
            self._last_error = f"Request timed out after {self._last_latency_ms}ms"
            self._is_healthy = False
            logger.warning(f"[{self.name}] {self._last_error}")
            return self._empty_batch(market)
        except Exception as e:
            self._last_latency_ms = int((time.monotonic() - start_time) * 1000)
            self._last_error = f"Unexpected exception: {str(e)}"
            self._is_healthy = False
            logger.error(f"[{self.name}] {self._last_error}", exc_info=True)
            return self._empty_batch(market)

    def normalize_results(self, raw_data: Any, market: str = "IN") -> List[TrendingTrack]:
        """
        Parses videos, cleans titles and artist names, and aggregates multiple uploads
        of the same canonical track into a unified trending entry.
        """
        if not isinstance(raw_data, dict):
            return []

        items = raw_data.get("items", [])
        if not items:
            return []

        # Multi-upload aggregation map: canonical_key -> aggregated info
        aggregated: Dict[str, Dict[str, Any]] = {}
        now_iso = datetime.now(timezone.utc).isoformat()

        for idx, item in enumerate(items):
            try:
                vid_id = item.get("id")
                snippet = item.get("snippet", {})
                stats = item.get("statistics", {})

                raw_title = snippet.get("title", "")
                channel_title = snippet.get("channelTitle", "")
                if not raw_title or not vid_id:
                    continue

                clean_title, artist = TrackNormalizer.extract_artist_and_title(raw_title, channel_title)
                c_key = TrackNormalizer.canonical_key(clean_title, artist)

                views = int(stats.get("viewCount", 0))
                likes = int(stats.get("likeCount", 0))

                thumbnails = snippet.get("thumbnails", {})
                best_thumb = (
                    thumbnails.get("high", {}).get("url")
                    or thumbnails.get("medium", {}).get("url")
                    or thumbnails.get("default", {}).get("url")
                    or f"https://i.ytimg.com/vi/{vid_id}/hqdefault.jpg"
                )

                if c_key in aggregated:
                    # Multi-upload aggregation: combine views & likes
                    existing = aggregated[c_key]
                    existing["views"] += views
                    existing["likes"] += likes
                    existing["uploadCount"] += 1
                    # Retain highest quality thumbnail
                    if not existing.get("thumbnailUrl"):
                        existing["thumbnailUrl"] = best_thumb
                else:
                    aggregated[c_key] = {
                        "trackId": vid_id,
                        "title": clean_title,
                        "artist": artist,
                        "views": views,
                        "likes": likes,
                        "uploadCount": 1,
                        "chartPosition": idx + 1,
                        "thumbnailUrl": best_thumb,
                        "publishedAt": snippet.get("publishedAt", "")
                    }
            except Exception as item_err:
                logger.debug(f"Skipping malformed YouTube item at index {idx}: {item_err}")
                continue

        # Sort aggregated canonical tracks by official chart position
        sorted_tracks = sorted(
            aggregated.values(),
            key=lambda x: x["chartPosition"]
        )

        total_items = max(len(sorted_tracks), 1)
        result: List[TrendingTrack] = []

        for rank, entry in enumerate(sorted_tracks, start=1):
            rank_factor = 1.0 - ((rank - 1) / total_items)
            views_factor = min(1.0, math.log10(max(entry["views"], 1000)) / 9.0)
            trend_score = round((0.7 * rank_factor) + (0.3 * views_factor), 4)

            # High views with recent chart position yields high momentum
            momentum_score = round(min(1.0, max(0.0, views_factor * (1.1 - (rank / total_items)))), 4)

            result.append(
                TrendingTrack(
                    trackId=entry["trackId"],
                    title=entry["title"],
                    artist=entry["artist"],
                    normalizedTitle=TrackNormalizer.strip_punctuation(TrackNormalizer.clean_text(entry["title"])),
                    normalizedArtist=TrackNormalizer.strip_punctuation(TrackNormalizer.clean_text(entry["artist"])),
                    trendScore=trend_score,
                    momentumScore=momentum_score,
                    source=self.name,
                    rank=rank,
                    confidence=0.95,
                    updatedAt=now_iso,
                    thumbnailUrl=entry.get("thumbnailUrl"),
                    metadata={
                        "views": entry["views"],
                        "likes": entry["likes"],
                        "uploadCount": entry["uploadCount"],
                        "originalChartRank": entry["chartPosition"],
                        "publishedAt": entry.get("publishedAt", "")
                    }
                )
            )

        return result

    def report_provider_status(self) -> ProviderStatus:
        return ProviderStatus(
            providerName=self.name,
            isConfigured=bool(self._api_key),
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
