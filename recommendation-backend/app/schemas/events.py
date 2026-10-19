from enum import Enum
from typing import List, Optional
from pydantic import BaseModel, Field

class EventType(str, Enum):
    PLAY_STARTED = "play_started"
    PLAYBACK_PROGRESS = "playback_progress"
    PLAY_COMPLETED = "play_completed"
    SKIPPED = "skipped"
    REPLAYED = "replayed"
    LIKED = "liked"
    UNLIKED = "unliked"
    PLAYLIST_ADDED = "playlist_added"
    PLAYLIST_REMOVED = "playlist_removed"
    RECOMMENDATION_IMPRESSION = "recommendation_impression"
    RECOMMENDATION_CLICKED = "recommendation_clicked"

class ListeningEvent(BaseModel):
    eventId: str = Field(..., description="Unique client or server generated event identifier")
    userId: str = Field(..., description="Pseudonymous user ID from Firebase Auth")
    trackId: str = Field(..., description="Stable Sielo / YouTube Music track ID")
    eventType: EventType
    timestampMs: int = Field(..., description="Unix epoch timestamp in milliseconds")
    playbackPositionMs: int = Field(default=0, ge=0)
    durationMs: int = Field(default=0, ge=0)
    trackTitle: Optional[str] = None
    trackArtist: Optional[str] = None
    trackAlbum: Optional[str] = None
    source: Optional[str] = Field(default="autoplay", description="Queue source e.g. autoplay, user_queue, album, search")
    sessionId: Optional[str] = None
    recommendationRequestId: Optional[str] = None
    recommendationPosition: Optional[int] = None

class BatchEventsRequest(BaseModel):
    events: List[ListeningEvent]

class BatchEventsResponse(BaseModel):
    accepted: int
    duplicatesIgnored: int
    errors: int
    message: str = "Events processed successfully"
