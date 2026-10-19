from abc import ABC, abstractmethod
from typing import Optional, Dict, Any
from pydantic import BaseModel, Field

class AudioFeatures(BaseModel):
    available: bool = False
    source: Optional[str] = None
    bpm: Optional[float] = None
    musicalKey: Optional[str] = None
    energy: Optional[float] = Field(default=None, ge=0.0, le=1.0)
    danceability: Optional[float] = Field(default=None, ge=0.0, le=1.0)
    valence_mood: Optional[float] = Field(default=None, ge=0.0, le=1.0)
    acousticness: Optional[float] = Field(default=None, ge=0.0, le=1.0)
    confidence: float = 0.0

class AudioFeatureProvider(ABC):
    """
    Abstract interface for authorized audio feature extraction / lookup.
    Never fabricates BPM, key, or energy when unauthorized or unavailable.
    """
    @abstractmethod
    def get_features(self, track_id: str, track_metadata: Dict[str, Any]) -> AudioFeatures:
        pass

class DefaultAudioFeatureProvider(AudioFeatureProvider):
    """
    Default compliance-safe audio provider.
    Since YouTube Music streams do not grant extraction licenses,
    this provider explicitly declares features as UNAVAILABLE without fabricating values.
    """
    def __init__(self):
        self._cache: Dict[str, AudioFeatures] = {}

    def get_features(self, track_id: str, track_metadata: Dict[str, Any]) -> AudioFeatures:
        if track_id in self._cache:
            return self._cache[track_id]
        
        # Explicit unavailable state — no fabricated BPM or keys
        features = AudioFeatures(
            available=False,
            source="none",
            bpm=None,
            musicalKey=None,
            energy=None,
            confidence=0.0
        )
        self._cache[track_id] = features
        return features

audio_provider = DefaultAudioFeatureProvider()
