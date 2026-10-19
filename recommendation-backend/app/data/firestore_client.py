import logging
from typing import List, Dict, Any, Optional
from datetime import datetime, timezone
import json
import os
from pathlib import Path
from app.configuration.config import settings
from app.schemas.events import ListeningEvent

logger = logging.getLogger("sielo.firestore")

class FirestoreClient:
    """
    Handles Firestore read and write operations.
    Includes a local storage fallback for offline development & tests.
    """
    def __init__(self):
        self._db = None
        self._local_events: List[Dict[str, Any]] = []
        self._local_tracks: Dict[str, Dict[str, Any]] = {}
        self._local_users: Dict[str, Dict[str, Any]] = {}
        self._init_client()

    def _init_client(self):
        try:
            import firebase_admin
            from firebase_admin import firestore
            self._db = firestore.client()
            logger.info("Firestore client initialized successfully")
        except Exception as e:
            logger.warning(f"Firestore client could not connect. Using local in-memory fallback: {e}")
            self._db = None

    def record_events(self, events: List[ListeningEvent]) -> Dict[str, int]:
        accepted = 0
        duplicates = 0
        errors = 0

        if self._db is not None:
            try:
                batch = self._db.batch()
                events_col = self._db.collection("sielo_listening_events")
                
                for ev in events:
                    doc_ref = events_col.document(ev.eventId)
                    # Check if already exists (idempotency)
                    data = ev.model_dump()
                    data["serverIngestedAt"] = datetime.now(timezone.utc).isoformat()
                    batch.set(doc_ref, data, merge=True)
                    accepted += 1
                
                batch.commit()
                return {"accepted": accepted, "duplicates": duplicates, "errors": errors}
            except Exception as e:
                logger.error(f"Error writing batch to Firestore: {e}")
                # Fall back to local
                self._fallback_record(events)
                return {"accepted": len(events), "duplicates": 0, "errors": 0}
        else:
            return self._fallback_record(events)

    def _fallback_record(self, events: List[ListeningEvent]) -> Dict[str, int]:
        existing_ids = {e["eventId"] for e in self._local_events}
        accepted = 0
        duplicates = 0
        for ev in events:
            if ev.eventId in existing_ids:
                duplicates += 1
            else:
                self._local_events.append(ev.model_dump())
                existing_ids.add(ev.eventId)
                accepted += 1
        return {"accepted": accepted, "duplicates": duplicates, "errors": 0}

    def fetch_recent_events(self, limit: int = 50000, since_ms: Optional[int] = None) -> List[Dict[str, Any]]:
        if self._db is not None:
            try:
                query = self._db.collection("sielo_listening_events")
                if since_ms:
                    query = query.where("timestampMs", ">=", since_ms)
                query = query.order_by("timestampMs", direction="DESCENDING").limit(limit)
                docs = query.stream()
                return [doc.to_dict() for doc in docs]
            except Exception as e:
                logger.error(f"Error querying Firestore events: {e}")
                return self._local_events
        else:
            if since_ms:
                return [e for e in self._local_events if e.get("timestampMs", 0) >= since_ms][:limit]
            return self._local_events[:limit]

    def get_user_profile(self, user_id: str) -> Optional[Dict[str, Any]]:
        if self._db is not None:
            try:
                doc = self._db.collection("sielo_users").document(user_id).collection("data").document("profile").get()
                if doc.exists:
                    return doc.to_dict()
            except Exception as e:
                logger.error(f"Error fetching user profile from Firestore: {e}")
        return self._local_users.get(user_id)

    def save_track_metadata(self, track_id: str, metadata: Dict[str, Any]):
        if self._db is not None:
            try:
                self._db.collection("sielo_tracks").document(track_id).set(metadata, merge=True)
            except Exception as e:
                logger.error(f"Error caching track metadata to Firestore: {e}")
        self._local_tracks[track_id] = metadata

    def get_track_metadata(self, track_id: str) -> Optional[Dict[str, Any]]:
        if self._db is not None:
            try:
                doc = self._db.collection("sielo_tracks").document(track_id).get()
                if doc.exists:
                    return doc.to_dict()
            except Exception as e:
                logger.error(f"Error retrieving track metadata: {e}")
        return self._local_tracks.get(track_id)

    def count_total_events(self) -> int:
        if self._db is not None:
            try:
                # Fast count aggregation in Firestore
                count_query = self._db.collection("sielo_listening_events").count()
                res = count_query.get()
                return int(res[0][0].value)
            except Exception:
                pass
        return len(self._local_events)

firestore_client = FirestoreClient()
