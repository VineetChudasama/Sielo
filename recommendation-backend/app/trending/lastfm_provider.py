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

logger = logging.getLogger("sielo.trending.lastfm")

class LastFmTrendingProvider(TrendingProvider):
    """
    Fallback 1: Last.fm API Provider.
    Queries geo.getTopTracks for market-specific charts or chart.getTopTracks
    as global fallback when geo charts are unavailable.
    """

    LASTFM_API_ENDPOINT = "https://ws.audioscrobbler.com/2.0/"

    COUNTRY_MAP = {
        "IN": "India",
        "US": "United States",
        "GB": "United Kingdom",
        "CA": "Canada",
        "AU": "Australia",
        "DE": "Germany",
        "FR": "France",
        "JP": "Japan",
        "BR": "Brazil"
    }

    def __init__(self, api_key: Optional[str] = None):
        self._api_key = api_key or settings.LASTFM_API_KEY
        self._last_attempt_at: Optional[str] = None
        self._last_success_at: Optional[str] = None
        self._last_latency_ms: int = 0
        self._last_error: Optional[str] = None
        self._usable_count: int = 0
        self._is_healthy: bool = True

    @property
    def name(self) -> str:
        return "lastfm"

    async def fetch_trending(self, market: str = "IN") -> TrendingBatch:
        self._last_attempt_at = datetime.now(timezone.utc).isoformat()
        start_time = time.monotonic()

        if not self._api_key:
            self._last_error = "LASTFM_API_KEY not configured in environment"
            self._is_healthy = False
            self._usable_count = 0
            logger.info(f"[{self.name}] {self._last_error}")
            return self._empty_batch(market)

        country_name = self.COUNTRY_MAP.get(market.upper(), "India")
        params = {
            "method": "geo.gettoptracks",
            "country": country_name,
            "api_key": self._api_key,
            "format": "json",
            "limit": "50"
        }

        try:
            async with httpx.AsyncClient(timeout=8.0) as client:
                response = await client.get(self.LASTFM_API_ENDPOINT, params=params)
                self._last_latency_ms = int((time.monotonic() - start_time) * 1000)

                if response.status_code == 429:
                    self._last_error = "HTTP 429: Last.fm rate limit exceeded"
                    self._is_healthy = False
                    logger.warning(f"[{self.name}] {self._last_error}")
                    return self._empty_batch(market)

                # Fallback to chart.gettoptracks if geo fails or country not found
                if response.status_code != 200:
                    logger.info(f"[{self.name}] geo.gettoptracks failed (HTTP {response.status_code}), attempting chart.gettoptracks fallback...")
                    params["method"] = "chart.gettoptracks"
                    params.pop("country", None)
                    response = await client.get(self.LASTFM_API_ENDPOINT, params=params)

                if response.status_code != 200:
                    self._last_error = f"HTTP {response.status_code}: {response.text[:200]}"
                    self._is_healthy = False
                    logger.warning(f"[{self.name}] {self._last_error}")
                    return self._empty_batch(market)

                data = response.json()
                tracks = self.normalize_results(data, market=market)

                if not tracks:
                    self._last_error = "Zero usable tracks returned from Last.fm"
                    self._is_healthy = True
                    self._usable_count = 0
                    logger.info(f"[{self.name}] {self._last_error}")
                    return self._empty_batch(market)

                self._is_healthy = True
                self._last_error = None
                self._last_success_at = datetime.now(timezone.utc).isoformat()
                self._usable_count = len(tracks)
                logger.info(f"[{self.name}] Successfully fetched {len(tracks)} tracks for market={market} in {self._last_latency_ms}ms")

                return TrendingBatch(
                    market=market,
                    source=self.name,
                    generatedAt=datetime.now(timezone.utc).isoformat(),
                    isStale=False,
                    confidence=0.85,
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
        Extracts tracks from geo.gettoptracks or chart.gettoptracks JSON responses.
        Deduplicates tracks by canonical key and normalizes scores.
        """
        if not isinstance(raw_data, dict):
            return []

        # geo.gettoptracks returns { "tracks": { "track": [ ... ] } }
        tracks_obj = raw_data.get("tracks", {})
        track_list = tracks_obj.get("track", [])
        if not isinstance(track_list, list):
            return []

        now_iso = datetime.now(timezone.utc).isoformat()
        seen_keys = set()
        deduped = []

        for idx, item in enumerate(track_list):
            try:
                raw_title = item.get("name", "")
                artist_obj = item.get("artist", {})
                artist_name = artist_obj.get("name", "") if isinstance(artist_obj, dict) else str(artist_obj)

                if not raw_title or not artist_name:
                    continue

                clean_title = TrackNormalizer.clean_title(raw_title)
                c_key = TrackNormalizer.canonical_key(clean_title, artist_name)

                if c_key in seen_keys:
                    continue
                seen_keys.add(c_key)

                listeners = int(item.get("listeners", 0))
                playcount = int(item.get("playcount", 0))

                # Image extraction
                images = item.get("image", [])
                thumb_url = None
                if isinstance(images, list):
                    for img in reversed(images):
                        if isinstance(img, dict) and img.get("#text"):
                            thumb_url = img["#text"]
                            break

                deduped.append({
                    "title": clean_title,
                    "artist": artist_name,
                    "listeners": listeners,
                    "playcount": playcount,
                    "rank": idx + 1,
                    "thumbnailUrl": thumb_url
                })
            except Exception as item_err:
                logger.debug(f"Skipping malformed Last.fm item at index {idx}: {item_err}")
                continue

        total_items = max(len(deduped), 1)
        result: List[TrendingTrack] = []

        for rank, entry in enumerate(deduped, start=1):
            rank_factor = 1.0 - ((rank - 1) / total_items)
            listener_factor = min(1.0, math.log10(max(entry["listeners"], 100)) / 7.0)
            trend_score = round((0.75 * rank_factor) + (0.25 * listener_factor), 4)

            # Last.fm charts change relatively slowly, so assign steady momentum
            momentum_score = round(min(1.0, max(0.0, 0.4 + (0.4 * rank_factor))), 4)

            result.append(
                TrendingTrack(
                    trackId=None,
                    title=entry["title"],
                    artist=entry["artist"],
                    normalizedTitle=TrackNormalizer.strip_punctuation(TrackNormalizer.clean_text(entry["title"])),
                    normalizedArtist=TrackNormalizer.strip_punctuation(TrackNormalizer.clean_text(entry["artist"])),
                    trendScore=trend_score,
                    momentumScore=momentum_score,
                    source=self.name,
                    rank=rank,
                    confidence=0.85,
                    updatedAt=now_iso,
                    thumbnailUrl=entry.get("thumbnailUrl"),
                    metadata={
                        "listeners": entry["listeners"],
                        "playcount": entry["playcount"],
                        "originalChartRank": entry["rank"]
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
