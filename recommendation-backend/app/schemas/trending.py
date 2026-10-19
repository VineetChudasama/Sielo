from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field

class TrendingTrack(BaseModel):
    trackId: Optional[str] = Field(default=None, description="Platform or canonical track ID if known")
    title: str = Field(..., description="Canonical or cleaned track title")
    artist: str = Field(..., description="Primary artist name")
    normalizedTitle: str = Field(..., description="Lowercased, punctuation-stripped title for fast matching")
    normalizedArtist: str = Field(..., description="Lowercased, punctuation-stripped artist for fast matching")
    trendScore: float = Field(..., ge=0.0, le=1.0, description="Normalized popularity/trend score between 0.0 and 1.0")
    momentumScore: float = Field(default=0.0, ge=0.0, le=1.0, description="Recent growth momentum between 0.0 and 1.0")
    source: str = Field(..., description="Source provider: youtube, lastfm, community, snapshot")
    rank: int = Field(..., ge=1, description="Chart rank / position index (1-based)")
    confidence: float = Field(default=1.0, ge=0.0, le=1.0, description="Confidence in this trend signal (0.0 to 1.0)")
    updatedAt: str = Field(..., description="ISO-8601 timestamp of when this item was retrieved/generated")
    thumbnailUrl: Optional[str] = Field(default=None, description="Artwork or thumbnail URL if available")
    metadata: Dict[str, Any] = Field(default_factory=dict, description="Raw source statistics, e.g. views, listeners, plays")

class TrendingBatch(BaseModel):
    market: str = Field(..., description="Market / ISO country code, e.g. IN, US, GLOBAL")
    source: str = Field(..., description="Active source: youtube, lastfm, community, snapshot, none")
    generatedAt: str = Field(..., description="ISO-8601 timestamp of when this batch was composed")
    isStale: bool = Field(default=False, description="True if served from an aging cache or snapshot fallback")
    confidence: float = Field(default=1.0, ge=0.0, le=1.0, description="Overall batch confidence score")
    tracks: List[TrendingTrack] = Field(default_factory=list, description="Ranked list of trending tracks")

class ProviderStatus(BaseModel):
    providerName: str
    isConfigured: bool
    isHealthy: bool
    lastAttemptAt: Optional[str] = None
    lastSuccessAt: Optional[str] = None
    lastLatencyMs: int = 0
    lastError: Optional[str] = None
    usableCandidateCount: int = 0

class TrendingStatusResponse(BaseModel):
    activeSource: str
    market: str
    lastRefreshedAt: Optional[str] = None
    cacheAgeSeconds: int = 0
    isStale: bool = False
    providers: Dict[str, ProviderStatus] = Field(default_factory=dict)
    totalCandidates: int = 0
