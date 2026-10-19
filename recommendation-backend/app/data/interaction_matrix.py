import math
import numpy as np
import pandas as pd
from scipy.sparse import csr_matrix
from typing import List, Dict, Any, Tuple
from app.configuration.config import settings

def calculate_event_weight(
    event_type: str,
    duration_ms: int,
    playback_position_ms: int
) -> float:
    """
    Computes the implicit preference signal from a single interaction.
    """
    completion_ratio = (
        playback_position_ms / float(duration_ms) if duration_ms > 0 else 0.0
    )

    if event_type == "liked":
        return 2.5
    elif event_type == "playlist_added":
        return 2.0
    elif event_type == "replayed":
        return 1.8
    elif event_type == "play_completed":
        return 1.2
    elif event_type == "skipped":
        if completion_ratio < 0.15:
            # Harsh early skip penalty
            return -1.0
        elif completion_ratio < 0.40:
            return -0.4
        else:
            # Skipped near the end is not a strong dislike
            return 0.1
    elif event_type == "play_started":
        return 0.2
    elif event_type == "playback_progress":
        if completion_ratio >= 0.5:
            return 0.6
        elif completion_ratio >= 0.15:
            return 0.3
        return 0.05
    elif event_type == "unliked":
        return -1.5
    elif event_type == "playlist_removed":
        return -1.0
    return 0.0

def build_interaction_matrix(
    events: List[Dict[str, Any]],
    half_life_days: float = settings.HALF_LIFE_DAYS,
    current_time_ms: int = None
) -> Tuple[csr_matrix, Dict[str, int], Dict[str, int], Dict[int, str], Dict[int, str]]:
    """
    Aggregates events into a time-decayed, bounded sparse User-Track interaction matrix.
    Returns:
        (sparse_matrix, user_to_idx, track_to_idx, idx_to_user, idx_to_track)
    """
    if not events:
        empty_mat = csr_matrix((0, 0), dtype=np.float32)
        return empty_mat, {}, {}, {}, {}

    df = pd.DataFrame(events)
    if "userId" not in df.columns or "trackId" not in df.columns:
        empty_mat = csr_matrix((0, 0), dtype=np.float32)
        return empty_mat, {}, {}, {}, {}

    if current_time_ms is None:
        current_time_ms = int(df["timestampMs"].max()) if "timestampMs" in df.columns else 0

    # 1. Compute raw event scores
    weights = []
    for _, row in df.iterrows():
        base_w = calculate_event_weight(
            str(row.get("eventType", "")),
            int(row.get("durationMs", 0)),
            int(row.get("playbackPositionMs", 0))
        )
        
        # Apply exponential recency decay: weight * 2^(-dt / half_life)
        ts = int(row.get("timestampMs", current_time_ms))
        age_days = max(0.0, (current_time_ms - ts) / (1000.0 * 86400.0))
        decay = math.pow(0.5, age_days / half_life_days) if half_life_days > 0 else 1.0
        
        weights.append(base_w * decay)

    df["interaction_score"] = weights

    # 2. Group by (userId, trackId) and sum
    aggregated = df.groupby(["userId", "trackId"])["interaction_score"].sum().reset_index()

    # 3. Apply log-saturation to prevent hyper-repeat bias: log1p(max(0, score))
    # We clip negative scores slightly to avoid total corruption, while preserving negative preference
    aggregated["bounded_score"] = aggregated["interaction_score"].apply(
        lambda s: float(np.sign(s) * np.log1p(abs(s)))
    )

    # 4. Filter only meaningful interactions (drop near-zero noise)
    aggregated = aggregated[abs(aggregated["bounded_score"]) > 0.05]

    unique_users = sorted(aggregated["userId"].unique())
    unique_tracks = sorted(aggregated["trackId"].unique())

    user_to_idx = {u: i for i, u in enumerate(unique_users)}
    track_to_idx = {t: i for i, t in enumerate(unique_tracks)}
    idx_to_user = {i: u for u, i in user_to_idx.items()}
    idx_to_track = {i: t for t, i in track_to_idx.items()}

    row_indices = [user_to_idx[u] for u in aggregated["userId"]]
    col_indices = [track_to_idx[t] for t in aggregated["trackId"]]
    data_values = aggregated["bounded_score"].astype(np.float32).values

    num_users = len(unique_users)
    num_tracks = len(unique_tracks)

    sparse_mat = csr_matrix((data_values, (row_indices, col_indices)), shape=(num_users, num_tracks), dtype=np.float32)

    return sparse_mat, user_to_idx, track_to_idx, idx_to_user, idx_to_track
