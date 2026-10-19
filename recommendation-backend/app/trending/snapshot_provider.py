import time
import logging
from typing import Any, List, Dict, Optional
from datetime import datetime, timezone

from app.schemas.trending import TrendingBatch, TrendingTrack, ProviderStatus
from app.trending.provider_base import TrendingProvider
from app.trending.normalizer import TrackNormalizer
from app.data.firestore_client import firestore_client

logger = logging.getLogger("sielo.trending.snapshot")

class SnapshotTrendingProvider(TrendingProvider):
    """
    Fallback 3: Last-Known-Good Snapshot Provider.
    Persists and loads previously verified trending batches from durable Firestore storage.
    Applies graceful confidence decay as data ages to avoid presenting stale trends as real-time.
    """

    COLLECTION_SNAPSHOTS = "sielo_trending_snapshots"

    def __init__(self, client = None):
        self._firestore = client or firestore_client
        self._memory_snapshot: Dict[str, Dict[str, Any]] = {}  # market -> snapshot dict
        self._last_attempt_at: Optional[str] = None
        self._last_success_at: Optional[str] = None
        self._last_latency_ms: int = 0
        self._last_error: Optional[str] = None
        self._usable_count: int = 0
        self._is_healthy: bool = True

    @property
    def name(self) -> str:
        return "snapshot"

    def save_snapshot(self, batch: TrendingBatch) -> bool:
        """
        Saves a validated live trending batch into durable storage.
        Guards: Never overwrites an existing snapshot with an empty or low-confidence batch!
        """
        if not batch or len(batch.tracks) < 3:
            logger.debug(f"[{self.name}] Refusing to save empty or undersized batch to durable snapshot")
            return False

        market = batch.market.upper()
        now_ms = int(time.time() * 1000)
        now_iso = datetime.now(timezone.utc).isoformat()

        doc_data = {
            "market": market,
            "source": batch.source,
            "sourceTimestamp": batch.generatedAt,
            "savedAtMs": now_ms,
            "savedAtIso": now_iso,
            "initialConfidence": batch.confidence,
            "trackCount": len(batch.tracks),
            "tracks": [t.model_dump() for t in batch.tracks]
        }

        # Keep in-memory cache
        self._memory_snapshot[market] = doc_data

        # Persist to Firestore
        if self._firestore._db is not None:
            try:
                doc_ref = self._firestore._db.collection(self.COLLECTION_SNAPSHOTS).document(f"snapshot_{market}")
                doc_ref.set(doc_data)
                logger.info(f"[{self.name}] Saved durable snapshot for market={market} ({len(batch.tracks)} tracks from {batch.source})")
                return True
            except Exception as e:
                logger.error(f"[{self.name}] Failed to write snapshot to Firestore: {e}")
                return False
        return True

    async def fetch_trending(self, market: str = "IN") -> TrendingBatch:
        self._last_attempt_at = datetime.now(timezone.utc).isoformat()
        start_time = time.monotonic()
        market_key = market.upper()

        try:
            snapshot_data = None

            # 1. Try Firestore durable collection first
            if self._firestore._db is not None:
                try:
                    doc = self._firestore._db.collection(self.COLLECTION_SNAPSHOTS).document(f"snapshot_{market_key}").get()
                    if doc.exists:
                        snapshot_data = doc.to_dict()
                except Exception as e:
                    logger.warning(f"[{self.name}] Error reading Firestore snapshot: {e}")

            # 2. Fall back to memory snapshot
            if not snapshot_data:
                snapshot_data = self._memory_snapshot.get(market_key)

            self._last_latency_ms = int((time.monotonic() - start_time) * 1000)

            if not snapshot_data or not snapshot_data.get("tracks"):
                self._last_error = f"No durable snapshot found for market={market}"
                self._is_healthy = False
                self._usable_count = 0
                logger.warning(f"[{self.name}] {self._last_error}")
                return self._empty_batch(market)

            # Check age and apply staleness decay
            saved_at_ms = snapshot_data.get("savedAtMs", 0)
            now_ms = int(time.time() * 1000)
            age_ms = max(0, now_ms - saved_at_ms)
            age_hours = age_ms / (1000 * 60 * 60)

            raw_confidence = float(snapshot_data.get("initialConfidence", 0.9))

            # Confidence decay schedule:
            # < 6h: 100% confidence, isStale=False
            # 6h - 24h: 80% confidence, isStale=True
            # 24h - 72h: 50% confidence, isStale=True
            # 72h - 7d: 25% confidence, isStale=True
            # > 7d: Expired, confidence=0.0
            if age_hours <= 6.0:
                confidence = raw_confidence
                is_stale = False
            elif age_hours <= 24.0:
                confidence = round(raw_confidence * 0.8, 3)
                is_stale = True
            elif age_hours <= 72.0:
                confidence = round(raw_confidence * 0.5, 3)
                is_stale = True
            elif age_hours <= 168.0:  # 7 days
                confidence = round(raw_confidence * 0.25, 3)
                is_stale = True
            else:
                self._last_error = f"Durable snapshot is older than 7 days ({age_hours:.1f} hours). Expired."
                self._is_healthy = True
                self._usable_count = 0
                logger.info(f"[{self.name}] {self._last_error}")
                return self._empty_batch(market)

            tracks = self.normalize_results(snapshot_data.get("tracks", []), market=market)

            # Scale track confidence with batch confidence
            scaled_tracks = [
                t.model_copy(update={"confidence": confidence, "source": self.name})
                for t in tracks
            ]

            self._is_healthy = True
            self._last_error = None
            self._last_success_at = datetime.now(timezone.utc).isoformat()
            self._usable_count = len(scaled_tracks)

            logger.info(f"[{self.name}] Loaded snapshot for market={market} ({len(scaled_tracks)} tracks, age={age_hours:.1f}h, confidence={confidence})")

            return TrendingBatch(
                market=market,
                source=self.name,
                generatedAt=snapshot_data.get("sourceTimestamp", datetime.now(timezone.utc).isoformat()),
                isStale=is_stale,
                confidence=confidence,
                tracks=scaled_tracks
            )

        except Exception as e:
            self._last_latency_ms = int((time.monotonic() - start_time) * 1000)
            self._last_error = f"Failed to load snapshot: {str(e)}"
            self._is_healthy = False
            logger.error(f"[{self.name}] {self._last_error}", exc_info=True)
            return self._empty_batch(market)

    def normalize_results(self, raw_data: Any, market: str = "IN") -> List[TrendingTrack]:
        if not isinstance(raw_data, list):
            return []

        result: List[TrendingTrack] = []
        for item in raw_data:
            try:
                if isinstance(item, dict):
                    result.append(TrendingTrack(**item))
            except Exception:
                continue
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
            isStale=True,
            confidence=0.0,
            tracks=[]
        )
