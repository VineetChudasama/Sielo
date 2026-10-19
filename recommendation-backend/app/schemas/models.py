from typing import Dict, Optional
from pydantic import BaseModel

class EvaluationMetrics(BaseModel):
    precision_at_k: float = 0.0
    recall_at_k: float = 0.0
    ndcg_at_k: float = 0.0
    catalog_coverage: float = 0.0
    artist_diversity: float = 0.0
    evaluated_users_count: int = 0
    k: int = 10

class TrainingRunRecord(BaseModel):
    runId: str
    startedAtMs: int
    completedAtMs: Optional[int] = None
    modelVersion: str
    status: str  # pending, training, evaluated, promoted, failed, rolled_back
    numUsers: int = 0
    numTracks: int = 0
    numInteractions: int = 0
    metrics: Optional[EvaluationMetrics] = None
    errorMessage: Optional[str] = None

class ModelStatusResponse(BaseModel):
    activeModelVersion: str
    isFallbackActive: bool
    lastTrainedAtMs: Optional[int] = None
    metrics: Optional[EvaluationMetrics] = None
    totalUsersIndexed: int = 0
    totalTracksIndexed: int = 0
    numCandidateModels: int = 0
