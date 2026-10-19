import logging
from fastapi import APIRouter, Depends, HTTPException
from app.auth.firebase_auth import verify_admin_key
from app.schemas.models import ModelStatusResponse, TrainingRunRecord
from app.training.model_store import model_store
from app.training.pipeline import training_pipeline

logger = logging.getLogger("sielo.api.admin")
router = APIRouter(prefix="/v1", tags=["Administration & Models"])

@router.get("/models/status", response_model=ModelStatusResponse)
async def get_model_status():
    manifest = model_store.get_status()
    active_ver = manifest.get("active_version", "baseline-v1")
    ver_info = manifest.get("versions", {}).get(active_ver, {})
    meta = ver_info.get("metadata", {})

    active_cf = model_store.load_active_model()

    return ModelStatusResponse(
        activeModelVersion=active_ver,
        isFallbackActive=not active_cf.is_trained,
        lastTrainedAtMs=int(meta.get("numInteractions", 0)),
        metrics=meta.get("metrics"),
        totalUsersIndexed=len(active_cf.user_to_idx),
        totalTracksIndexed=len(active_cf.track_to_idx),
        numCandidateModels=len(manifest.get("versions", {}))
    )

@router.post("/admin/training/run", response_model=TrainingRunRecord)
async def trigger_training(authorized: bool = Depends(verify_admin_key)):
    """
    Manually triggers a new model retraining run.
    """
    record = training_pipeline.run_training_job(trigger_source="admin_api")
    return record

@router.post("/admin/training/rollback")
async def rollback_model(authorized: bool = Depends(verify_admin_key)):
    rolled_back_version = model_store.rollback_to_previous()
    if not rolled_back_version:
        raise HTTPException(status_code=400, detail="No previous known-good model found to roll back to.")
    return {"message": "Rollback successful", "activeModelVersion": rolled_back_version}
