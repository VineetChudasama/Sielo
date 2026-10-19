import time
import asyncio
import logging
from typing import Dict, Tuple, Optional, List
from datetime import datetime, timezone

from app.configuration.config import settings
from app.schemas.trending import (
    TrendingBatch,
    TrendingTrack,
    TrendingStatusResponse,
    ProviderStatus
)
from app.trending.provider_base import TrendingProvider
from app.trending.youtube_provider import YouTubeTrendingProvider
from app.trending.lastfm_provider import LastFmTrendingProvider
from app.trending.community_provider import FirestoreCommunityTrendingProvider
from app.trending.snapshot_provider import SnapshotTrendingProvider

logger = logging.getLogger("sielo.trending.orchestrator")

class TrendingOrchestrator:
    """
    Coordinates primary and fallback trending providers:
    1. Primary: YouTube Data API v3
    2. Fallback 1: Last.fm API
    3. Fallback 2: Firestore Community Trending
    4. Fallback 3: Last-Known-Good Durable Snapshot
    Implements in-memory caching with stale-while-revalidate and rate-limit cooldowns.
    """

    def __init__(
        self,
        youtube_provider: Optional[YouTubeTrendingProvider] = None,
        lastfm_provider: Optional[LastFmTrendingProvider] = None,
        community_provider: Optional[FirestoreCommunityTrendingProvider] = None,
        snapshot_provider: Optional[SnapshotTrendingProvider] = None
    ):
        self.youtube = youtube_provider or YouTubeTrendingProvider()
        self.lastfm = lastfm_provider or LastFmTrendingProvider()
        self.community = community_provider or FirestoreCommunityTrendingProvider()
        self.snapshot = snapshot_provider or SnapshotTrendingProvider()

        # Chain in strict priority order
        self.live_providers: List[TrendingProvider] = [
            self.youtube,
            self.lastfm,
            self.community
        ]

        # In-memory cache: market -> (TrendingBatch, timestamp_seconds)
        self._cache: Dict[str, Tuple[TrendingBatch, float]] = {}
        # Cooldown trackers: provider_name -> cooldown_until_timestamp_seconds
        self._provider_cooldowns: Dict[str, float] = {}
        self._active_source: str = "none"
        self._lock = asyncio.Lock()

    def _is_in_cooldown(self, provider_name: str) -> bool:
        cooldown_until = self._provider_cooldowns.get(provider_name, 0.0)
        return time.time() < cooldown_until

    def _set_cooldown(self, provider_name: str, seconds: float):
        self._provider_cooldowns[provider_name] = time.time() + seconds

    async def get_trending(self, market: str = "IN", force_refresh: bool = False) -> TrendingBatch:
        market_key = market.upper()
        now = time.time()
        ttl_seconds = settings.TRENDING_CACHE_TTL_MINUTES * 60

        # 1. Return from in-memory cache if fresh
        if not force_refresh and market_key in self._cache:
            batch, cached_at = self._cache[market_key]
            cache_age = now - cached_at
            if cache_age < ttl_seconds:
                # Fresh cache hit!
                return batch

        # 2. Revalidate / Refresh through provider chain
        async with self._lock:
            # Recheck cache inside lock to prevent thundering herd
            if not force_refresh and market_key in self._cache:
                batch, cached_at = self._cache[market_key]
                if (now - cached_at) < ttl_seconds:
                    return batch

            refreshed_batch = await self._refresh_chain(market_key)
            self._cache[market_key] = (refreshed_batch, time.time())
            self._active_source = refreshed_batch.source
            return refreshed_batch

    async def _refresh_chain(self, market: str) -> TrendingBatch:
        """
        Executes the fallback chain:
        YouTube -> Last.fm -> Community -> Snapshot -> Empty
        """
        logger.info(f"[Orchestrator] Starting trending refresh chain for market={market}...")

        # 1. Try each live provider in strict priority
        for provider in self.live_providers:
            p_name = provider.name
            if self._is_in_cooldown(p_name):
                logger.info(f"[Orchestrator] Skipping provider '{p_name}' (active rate-limit cooldown)")
                continue

            try:
                logger.info(f"[Orchestrator] Attempting provider '{p_name}' for market={market}...")
                batch = await provider.fetch_trending(market)

                # Validation: Requires at least 3 usable candidates
                if batch and len(batch.tracks) >= 3 and batch.confidence > 0.0:
                    logger.info(f"[Orchestrator] Provider '{p_name}' SUCCEEDED with {len(batch.tracks)} tracks (confidence={batch.confidence})")
                    # Immediately save this validated live batch into durable snapshot!
                    self.snapshot.save_snapshot(batch)
                    return batch

                # If provider returned 429 or critical error, apply cooldown
                status = provider.report_provider_status()
                if status.lastError and "429" in status.lastError:
                    self._set_cooldown(p_name, 15 * 60)  # 15 min cooldown on 429
                elif not status.isHealthy:
                    self._set_cooldown(p_name, 2 * 60)   # 2 min cooldown on general failure

                logger.info(f"[Orchestrator] Provider '{p_name}' yielded insufficient data ({len(batch.tracks) if batch else 0} tracks). Advancing to next fallback...")

            except Exception as e:
                logger.warning(f"[Orchestrator] Exception while querying provider '{p_name}': {e}", exc_info=True)
                self._set_cooldown(p_name, 2 * 60)

        # 2. All live providers failed or yielded insufficient data -> Fallback 3: Snapshot
        logger.info(f"[Orchestrator] All live providers exhausted. Falling back to durable snapshot...")
        try:
            snapshot_batch = await self.snapshot.fetch_trending(market)
            if snapshot_batch and len(snapshot_batch.tracks) >= 3 and snapshot_batch.confidence > 0.0:
                logger.info(f"[Orchestrator] Durable snapshot loaded successfully with {len(snapshot_batch.tracks)} tracks (confidence={snapshot_batch.confidence}, isStale={snapshot_batch.isStale})")
                return snapshot_batch
        except Exception as snap_err:
            logger.error(f"[Orchestrator] Snapshot provider error: {snap_err}")

        # 3. Everything unavailable (e.g. completely offline, no snapshot yet)
        logger.warning(f"[Orchestrator] No trending data available from any provider or snapshot for market={market}.")
        return TrendingBatch(
            market=market,
            source="none",
            generatedAt=datetime.now(timezone.utc).isoformat(),
            isStale=True,
            confidence=0.0,
            tracks=[]
        )

    def report_status(self, market: str = "IN") -> TrendingStatusResponse:
        market_key = market.upper()
        now = time.time()
        cached_info = self._cache.get(market_key)

        cache_age = int(now - cached_info[1]) if cached_info else 0
        batch = cached_info[0] if cached_info else None

        providers_status = {
            self.youtube.name: self.youtube.report_provider_status(),
            self.lastfm.name: self.lastfm.report_provider_status(),
            self.community.name: self.community.report_provider_status(),
            self.snapshot.name: self.snapshot.report_provider_status()
        }

        return TrendingStatusResponse(
            activeSource=batch.source if batch else self._active_source,
            market=market_key,
            lastRefreshedAt=batch.generatedAt if batch else None,
            cacheAgeSeconds=cache_age,
            isStale=batch.isStale if batch else True,
            providers=providers_status,
            totalCandidates=len(batch.tracks) if batch else 0
        )

# Global singleton orchestrator
trending_orchestrator = TrendingOrchestrator()
