import os
from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    APP_NAME: str = "Sielo Recommendation Engine"
    API_V1_PREFIX: str = "/v1"
    DEBUG: bool = False
    
    # Environment & Firebase
    ENVIRONMENT: str = "development"
    FIREBASE_CREDENTIALS_PATH: str = ""
    FIREBASE_PROJECT_ID: str = "sielo-music"
    ALLOW_MOCK_AUTH_FOR_TESTS: bool = True
    
    # Storage & Model Artifacts
    BASE_DIR: Path = Path(__file__).resolve().parent.parent.parent
    MODEL_DIR: Path = BASE_DIR / "models"
    DATA_DIR: Path = BASE_DIR / "data"
    
    # Collaborative Filtering & Implicit Feedback
    HALF_LIFE_DAYS: float = 21.0
    MIN_INTERACTIONS_FOR_CF: int = 10
    MIN_USERS_FOR_CF: int = 3
    MIN_ITEMS_FOR_CF: int = 5
    
    # Hybrid Weights (Normalized dynamically if a source is unavailable)
    WEIGHT_COLLABORATIVE: float = 0.40
    WEIGHT_PERSONAL_PREFERENCE: float = 0.30
    WEIGHT_CONTENT_SIMILARITY: float = 0.20
    WEIGHT_DISCOVERY: float = 0.10
    WEIGHT_AUDIO: float = 0.00  # Default 0 until authorized audio intelligence is available
    
    # Scheduling & Training
    RETRAIN_INTERVAL_HOURS: int = 24
    MIN_NEW_EVENTS_TO_RETRAIN: int = 50
    
    # Rate Limiting & Admin
    ADMIN_API_KEY: str = "sielo-dev-admin-secret-change-in-prod"
    RATE_LIMIT_PER_MINUTE: int = 120

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore"
    )

settings = Settings()
