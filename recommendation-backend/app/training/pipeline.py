import time
import logging
import threading
from datetime import datetime, timezone
from typing import Dict, Any, Optional
from app.configuration.config import settings
from app.data.firestore_client import firestore_client
from app.data.interaction_matrix import build_interaction_matrix
from app.recommendation.collaborative import CollaborativeRecommender
from app.evaluation.evaluator import ModelEvaluator
from app.training.model_store import model_store
from app.schemas.models import TrainingRunRecord

logger = logging.getLogger("sielo.training")

class TrainingPipeline:
    def __init__(self):
        self._lock = threading.Lock()
        self.last_run_record: Optional[TrainingRunRecord] = None

    def run_training_job(self, trigger_source: str = "manual") -> TrainingRunRecord:
        if not self._lock.acquire(blocking=False):
            logger.warning("Training job already in progress. Skipping trigger.")
            return TrainingRunRecord(
                runId=f"run-{int(time.time())}",
                startedAtMs=int(time.time() * 1000),
                modelVersion="skipped",
                status="skipped_already_running",
                errorMessage="Another training job is currently running."
            )

        start_time_ms = int(time.time() * 1000)
        run_id = f"train-{int(time.time())}"
        new_version = f"cf-v{datetime.now().strftime('%Y%m%d%H%M')}"

        try:
            logger.info(f"Starting model training job {run_id} (trigger: {trigger_source})")

            # 1. Fetch interaction events from Firestore / local store
            events = firestore_client.fetch_recent_events(limit=100000)
            if not events:
                logger.info("No listening events found. Aborting training run.")
                record = TrainingRunRecord(
                    runId=run_id,
                    startedAtMs=start_time_ms,
                    completedAtMs=int(time.time() * 1000),
                    modelVersion="none",
                    status="aborted_no_data",
                    errorMessage="Zero events available in database."
                )
                self.last_run_record = record
                return record

            # 2. Build Interaction Matrix
            sparse_mat, user_to_idx, track_to_idx, idx_to_user, idx_to_track = build_interaction_matrix(
                events, half_life_days=settings.HALF_LIFE_DAYS
            )

            num_users = len(user_to_idx)
            num_tracks = len(track_to_idx)
            num_interactions = sparse_mat.nnz

            logger.info(f"Interaction matrix built: {num_users} users, {num_tracks} tracks, {num_interactions} interactions.")

            # 3. Check Minimum Data Thresholds
            if num_users < settings.MIN_USERS_FOR_CF or num_tracks < settings.MIN_ITEMS_FOR_CF:
                msg = f"Insufficient data for collaborative filtering (users: {num_users}/{settings.MIN_USERS_FOR_CF}, tracks: {num_tracks}/{settings.MIN_ITEMS_FOR_CF}). Preserving fallback."
                logger.warning(msg)
                record = TrainingRunRecord(
                    runId=run_id,
                    startedAtMs=start_time_ms,
                    completedAtMs=int(time.time() * 1000),
                    modelVersion="fallback",
                    status="skipped_sparse_data",
                    numUsers=num_users,
                    numTracks=num_tracks,
                    numInteractions=num_interactions,
                    errorMessage=msg
                )
                self.last_run_record = record
                return record

            # 4. Train Candidate Collaborative Model
            candidate_model = CollaborativeRecommender(model_version=new_version)
            candidate_model.fit(sparse_mat, user_to_idx, track_to_idx, idx_to_user, idx_to_track)

            # 5. Evaluate Candidate Model
            # Generate top-10 predictions for evaluated users
            user_recs = {}
            ground_truth = {}
            for u_id in list(user_to_idx.keys())[:50]:
                recs = [t for t, _ in candidate_model.recommend_for_user(u_id, limit=10)]
                user_recs[u_id] = recs
                # Ground truth: tracks user interacted with positively
                u_idx = user_to_idx[u_id]
                played_indices = np_where_positive = (sparse_mat[u_idx].toarray().flatten() > 0.4).nonzero()[0]
                ground_truth[u_id] = {idx_to_track[idx] for idx in played_indices}

            # Map track artists
            track_artists = {}
            for ev in events:
                t_id = ev.get("trackId")
                art = ev.get("trackArtist")
                if t_id and art:
                    track_artists[t_id] = art

            metrics = ModelEvaluator.evaluate(
                user_recommendations=user_recs,
                ground_truth=ground_truth,
                all_catalog_tracks=set(track_to_idx.keys()),
                track_artists=track_artists,
                k=10
            )

            # 6. Save Candidate Model
            metadata = {
                "runId": run_id,
                "version": new_version,
                "trainedAt": datetime.now(timezone.utc).isoformat(),
                "numUsers": num_users,
                "numTracks": num_tracks,
                "numInteractions": num_interactions,
                "metrics": metrics.model_dump()
            }
            model_store.save_candidate_model(candidate_model, new_version, metadata)

            # 7. Safe Promotion: promote if model is valid
            model_store.promote_version(new_version)
            logger.info(f"Model version {new_version} promoted to production!")

            record = TrainingRunRecord(
                runId=run_id,
                startedAtMs=start_time_ms,
                completedAtMs=int(time.time() * 1000),
                modelVersion=new_version,
                status="promoted",
                numUsers=num_users,
                numTracks=num_tracks,
                numInteractions=num_interactions,
                metrics=metrics
            )
            self.last_run_record = record
            return record

        except Exception as e:
            logger.error(f"Training job {run_id} failed with error: {e}", exc_info=True)
            record = TrainingRunRecord(
                runId=run_id,
                startedAtMs=start_time_ms,
                completedAtMs=int(time.time() * 1000),
                modelVersion=new_version,
                status="failed",
                errorMessage=str(e)
            )
            self.last_run_record = record
            return record
        finally:
            self._lock.release()

training_pipeline = TrainingPipeline()
