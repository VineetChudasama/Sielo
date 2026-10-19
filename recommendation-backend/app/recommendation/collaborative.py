import logging
import numpy as np
from scipy.sparse import csr_matrix
from sklearn.metrics.pairwise import cosine_similarity
from typing import List, Dict, Tuple, Optional
from app.configuration.config import settings

logger = logging.getLogger("sielo.cf")

class CollaborativeRecommender:
    """
    Item-Item Implicit-Feedback Collaborative Filtering Engine.
    Computes track-track similarity from user interaction vectors and
    scores unlistened tracks for active users based on their listening history.
    """
    def __init__(self, model_version: str = "cf-v1"):
        self.model_version = model_version
        self.is_trained = False
        self.user_to_idx: Dict[str, int] = {}
        self.track_to_idx: Dict[str, int] = {}
        self.idx_to_user: Dict[int, str] = {}
        self.idx_to_track: Dict[int, str] = {}
        self.user_item_matrix: Optional[csr_matrix] = None
        self.item_similarity_matrix: Optional[np.ndarray] = None

    def fit(
        self,
        matrix: csr_matrix,
        user_to_idx: Dict[str, int],
        track_to_idx: Dict[str, int],
        idx_to_user: Dict[int, str],
        idx_to_track: Dict[int, str]
    ):
        self.user_item_matrix = matrix
        self.user_to_idx = user_to_idx
        self.track_to_idx = track_to_idx
        self.idx_to_user = idx_to_user
        self.idx_to_track = idx_to_track

        num_users, num_tracks = matrix.shape
        if num_users < settings.MIN_USERS_FOR_CF or num_tracks < settings.MIN_ITEMS_FOR_CF:
            logger.info(f"Dataset too sparse ({num_users} users, {num_tracks} tracks). Collaborative model in sparse mode.")
            self.is_trained = False
            return

        # Item-Item Cosine Similarity on Item vectors (columns of User-Item matrix)
        # Transpose so rows are items
        item_user_matrix = matrix.T.tocsr()
        # Compute pairwise item cosine similarities
        self.item_similarity_matrix = cosine_similarity(item_user_matrix, dense_output=True)
        # Zero out self-similarity on diagonal
        np.fill_diagonal(self.item_similarity_matrix, 0.0)
        self.is_trained = True
        logger.info(f"Collaborative filtering model fitted successfully across {num_users} users and {num_tracks} tracks.")

    def recommend_for_user(
        self,
        user_id: str,
        limit: int = 20,
        excluded_track_ids: Optional[List[str]] = None
    ) -> List[Tuple[str, float]]:
        """
        Generates collaborative filtering recommendations for a given user.
        Returns: [(track_id, normalized_score), ...]
        """
        if not self.is_trained or user_id not in self.user_to_idx:
            return []

        user_idx = self.user_to_idx[user_id]
        # User's historical interaction row: shape (1, num_tracks)
        user_profile = self.user_item_matrix.getrow(user_idx).toarray().flatten()

        # Score candidates: user_profile (1 x N) dot item_similarity (N x N) -> (1 x N)
        scores = user_profile.dot(self.item_similarity_matrix)

        # Exclude already heavily played or explicitly excluded tracks
        excluded_set = set(excluded_track_ids or [])
        already_played_indices = np.where(user_profile > 0.5)[0]
        for idx in already_played_indices:
            scores[idx] *= 0.1  # Downweight but don't strictly ban (user might like hearing favorites)

        for track_id in excluded_set:
            if track_id in self.track_to_idx:
                scores[self.track_to_idx[track_id]] = -1.0

        # Sort indices by descending score
        top_indices = np.argsort(-scores)
        results = []
        max_score = np.max(scores) if np.max(scores) > 0 else 1.0

        for idx in top_indices:
            s = float(scores[idx])
            if s <= 0.0:
                continue
            t_id = self.idx_to_track[idx]
            norm_score = min(1.0, max(0.0, s / max_score))
            results.append((t_id, norm_score))
            if len(results) >= limit:
                break

        return results

    def get_similar_tracks(
        self,
        track_id: str,
        limit: int = 10
    ) -> List[Tuple[str, float]]:
        """
        Retrieves tracks co-listened by users who listened to track_id.
        """
        if not self.is_trained or track_id not in self.track_to_idx:
            return []

        track_idx = self.track_to_idx[track_id]
        sim_scores = self.item_similarity_matrix[track_idx]
        top_indices = np.argsort(-sim_scores)

        results = []
        for idx in top_indices:
            score = float(sim_scores[idx])
            if score <= 0.001:
                break
            results.append((self.idx_to_track[idx], min(1.0, score)))
            if len(results) >= limit:
                break

        return results
