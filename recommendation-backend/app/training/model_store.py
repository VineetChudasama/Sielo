import os
import json
import pickle
import logging
from pathlib import Path
from typing import Optional, Tuple, Dict, Any
from app.configuration.config import settings
from app.recommendation.collaborative import CollaborativeRecommender

logger = logging.getLogger("sielo.model_store")

class ModelStore:
    """
    Manages model persistence, versioning, promotion, and rollbacks.
    """
    def __init__(self, storage_dir: Optional[Path] = None):
        self.storage_dir = storage_dir or settings.MODEL_DIR
        self.storage_dir.mkdir(parents=True, exist_ok=True)
        self.manifest_path = self.storage_dir / "models_manifest.json"
        self._init_manifest()

    def _init_manifest(self):
        if not self.manifest_path.exists():
            default_manifest = {
                "active_version": "baseline-v1",
                "last_known_good_version": "baseline-v1",
                "versions": {}
            }
            with open(self.manifest_path, "w", encoding="utf-8") as f:
                json.dump(default_manifest, f, indent=2)

    def _read_manifest(self) -> Dict[str, Any]:
        try:
            with open(self.manifest_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {"active_version": "baseline-v1", "versions": {}}

    def _write_manifest(self, manifest: Dict[str, Any]):
        with open(self.manifest_path, "w", encoding="utf-8") as f:
            json.dump(manifest, f, indent=2)

    def save_candidate_model(
        self,
        model: CollaborativeRecommender,
        version: str,
        metadata: Dict[str, Any]
    ) -> Path:
        version_dir = self.storage_dir / version
        version_dir.mkdir(parents=True, exist_ok=True)

        model_file = version_dir / "model.pkl"
        with open(model_file, "wb") as f:
            pickle.dump({
                "item_similarity_matrix": model.item_similarity_matrix,
                "user_to_idx": model.user_to_idx,
                "track_to_idx": model.track_to_idx,
                "idx_to_user": model.idx_to_user,
                "idx_to_track": model.idx_to_track,
                "is_trained": model.is_trained,
                "model_version": version
            }, f)

        meta_file = version_dir / "metadata.json"
        with open(meta_file, "w", encoding="utf-8") as f:
            json.dump(metadata, f, indent=2)

        manifest = self._read_manifest()
        manifest["versions"][version] = {
            "status": "candidate",
            "metadata": metadata,
            "path": str(model_file)
        }
        self._write_manifest(manifest)
        return model_file

    def promote_version(self, version: str):
        manifest = self._read_manifest()
        current_active = manifest.get("active_version")
        if current_active and current_active != version:
            manifest["last_known_good_version"] = current_active

        manifest["active_version"] = version
        if version in manifest.get("versions", {}):
            manifest["versions"][version]["status"] = "active"

        self._write_manifest(manifest)
        logger.info(f"Promoted model version {version} to active.")

    def rollback_to_previous(self) -> Optional[str]:
        manifest = self._read_manifest()
        last_good = manifest.get("last_known_good_version")
        if last_good and last_good in manifest.get("versions", {}):
            manifest["active_version"] = last_good
            self._write_manifest(manifest)
            logger.warning(f"Rolled back to last known good model: {last_good}")
            return last_good
        return None

    def load_active_model(self) -> CollaborativeRecommender:
        manifest = self._read_manifest()
        active_ver = manifest.get("active_version", "baseline-v1")
        version_info = manifest.get("versions", {}).get(active_ver)

        if not version_info:
            return CollaborativeRecommender(model_version="baseline-v1")

        model_path = Path(version_info.get("path", ""))
        if not model_path.exists():
            return CollaborativeRecommender(model_version="baseline-v1")

        try:
            with open(model_path, "rb") as f:
                data = pickle.load(f)
            model = CollaborativeRecommender(model_version=active_ver)
            model.item_similarity_matrix = data.get("item_similarity_matrix")
            model.user_to_idx = data.get("user_to_idx", {})
            model.track_to_idx = data.get("track_to_idx", {})
            model.idx_to_user = data.get("idx_to_user", {})
            model.idx_to_track = data.get("idx_to_track", {})
            model.is_trained = data.get("is_trained", False)
            return model
        except Exception as e:
            logger.error(f"Failed to load active model {active_ver}: {e}")
            return CollaborativeRecommender(model_version="baseline-v1")

    def get_status(self) -> Dict[str, Any]:
        return self._read_manifest()

model_store = ModelStore()
