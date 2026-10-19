from typing import List, Optional, Dict
from pydantic import BaseModel, Field

class RecommendationItem(BaseModel):
    trackId: str
    title: Optional[str] = None
    artist: Optional[str] = None
    album: Optional[str] = None
    thumbnailUrl: Optional[str] = None
    score: float = Field(..., ge=0.0, le=1.0)
    reason: str
    source: str = Field(default="hybrid", description="collaborative, content_based, personal_taste, or fallback")

class RecommendationRequest(BaseModel):
    userId: Optional[str] = None
    seedTrackId: Optional[str] = None
    seedArtist: Optional[str] = None
    seedTitle: Optional[str] = None
    excludedTrackIds: List[str] = Field(default_factory=list)
    limit: int = Field(default=20, ge=1, le=100)
    diversityFactor: float = Field(default=0.3, ge=0.0, le=1.0)
    preferredLanguage: Optional[str] = None

class RecommendationResponse(BaseModel):
    modelVersion: str
    isFallback: bool
    generatedAtMs: int
    recommendations: List[RecommendationItem]
    metadata: Dict[str, str] = Field(default_factory=dict)

class SimilarTracksResponse(BaseModel):
    seedTrackId: str
    modelVersion: str
    similarTracks: List[RecommendationItem]
