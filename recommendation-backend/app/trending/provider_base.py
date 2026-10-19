from abc import ABC, abstractmethod
from typing import Any, List
from app.schemas.trending import TrendingBatch, TrendingTrack, ProviderStatus

class TrendingProvider(ABC):
    """
    Common contract for all trending data providers (Primary, Fallback 1, 2, and 3).
    Allows providers to be swapped or reordered without changing ranking or orchestration.
    """

    @property
    @abstractmethod
    def name(self) -> str:
        """Unique provider identifier, e.g. 'youtube', 'lastfm', 'community', 'snapshot'"""
        pass

    @abstractmethod
    async def fetch_trending(self, market: str = "IN") -> TrendingBatch:
        """
        Retrieves raw data from the underlying source, validates it,
        and normalizes it into a standard TrendingBatch.
        """
        pass

    @abstractmethod
    def normalize_results(self, raw_data: Any, market: str = "IN") -> List[TrendingTrack]:
        """
        Converts provider-specific response payloads into normalized TrendingTrack instances.
        """
        pass

    @abstractmethod
    def report_provider_status(self) -> ProviderStatus:
        """
        Reports current health, latency, last success/failure timestamps, and candidate count.
        """
        pass
