import math
import numpy as np
from typing import List, Dict, Set, Tuple
from app.schemas.models import EvaluationMetrics

class ModelEvaluator:
    """
    Evaluates recommendation performance using Precision@K, Recall@K, NDCG@K,
    Catalog Coverage, and Artist Diversity.
    """
    @staticmethod
    def dcg_at_k(r: List[int], k: int) -> float:
        r = r[:k]
        return sum((rel / math.log2(idx + 2)) for idx, rel in enumerate(r))

    @staticmethod
    def ndcg_at_k(recommended: List[str], ground_truth: Set[str], k: int = 10) -> float:
        if not ground_truth:
            return 0.0
        relevance = [1 if item in ground_truth else 0 for item in recommended[:k]]
        actual_dcg = ModelEvaluator.dcg_at_k(relevance, k)
        ideal_relevance = sorted(relevance, reverse=True)
        ideal_dcg = ModelEvaluator.dcg_at_k(ideal_relevance, k)
        if ideal_dcg == 0.0:
            return 0.0
        return actual_dcg / ideal_dcg

    @classmethod
    def evaluate(
        cls,
        user_recommendations: Dict[str, List[str]],  # user_id -> top_k_track_ids
        ground_truth: Dict[str, Set[str]],          # user_id -> test_track_ids
        all_catalog_tracks: Set[str],
        track_artists: Dict[str, str],               # track_id -> artist_name
        k: int = 10
    ) -> EvaluationMetrics:
        if not user_recommendations or not ground_truth:
            return EvaluationMetrics(k=k)

        precisions = []
        recalls = []
        ndcgs = []
        all_recommended_tracks = set()
        artist_diversities = []

        for user_id, recs in user_recommendations.items():
            targets = ground_truth.get(user_id, set())
            if not targets:
                continue

            top_k = recs[:k]
            hits = sum(1 for t in top_k if t in targets)
            
            precisions.append(hits / float(k))
            recalls.append(hits / float(len(targets)))
            ndcgs.append(cls.ndcg_at_k(top_k, targets, k))
            all_recommended_tracks.update(top_k)

            # Artist diversity: ratio of unique artists in top-K
            artists = [track_artists.get(t, t) for t in top_k]
            if artists:
                artist_diversities.append(len(set(artists)) / float(len(artists)))

        num_evaluated = len(precisions)
        if num_evaluated == 0:
            return EvaluationMetrics(k=k)

        catalog_cov = (
            len(all_recommended_tracks) / float(len(all_catalog_tracks))
            if all_catalog_tracks else 0.0
        )

        return EvaluationMetrics(
            precision_at_k=round(float(np.mean(precisions)), 4),
            recall_at_k=round(float(np.mean(recalls)), 4),
            ndcg_at_k=round(float(np.mean(ndcgs)), 4),
            catalog_coverage=round(float(catalog_cov), 4),
            artist_diversity=round(float(np.mean(artist_diversities)), 4) if artist_diversities else 0.0,
            evaluated_users_count=num_evaluated,
            k=k
        )
