import logging
from fastapi import APIRouter, Depends, HTTPException, status
from app.auth.firebase_auth import get_current_user
from app.schemas.events import BatchEventsRequest, BatchEventsResponse
from app.data.firestore_client import firestore_client

logger = logging.getLogger("sielo.api.events")
router = APIRouter(prefix="/v1/events", tags=["Events"])

@router.post("", response_model=BatchEventsResponse)
async def ingest_listening_events(
    batch: BatchEventsRequest,
    auth_user_id: str = Depends(get_current_user)
):
    """
    Ingests a batch of listening events from the Sielo client.
    Enforces that events belong to the authenticated user and applies deduplication.
    """
    if not batch.events:
        return BatchEventsResponse(accepted=0, duplicatesIgnored=0, errors=0, message="Empty batch")

    # Security check: ensure userId matches authenticated user (or allow guest pseudonymous IDs)
    valid_events = []
    for ev in batch.events:
        if ev.userId != auth_user_id:
            # Re-stamp with authenticated user ID to prevent spoofing
            ev.userId = auth_user_id
        valid_events.append(ev)

    res = firestore_client.record_events(valid_events)

    return BatchEventsResponse(
        accepted=res.get("accepted", 0),
        duplicatesIgnored=res.get("duplicates", 0),
        errors=res.get("errors", 0),
        message="Batch events processed and recorded"
    )
