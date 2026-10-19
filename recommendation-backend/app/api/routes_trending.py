import logging
from typing import Optional
from fastapi import APIRouter, Query, Header, HTTPException, Depends
from app.configuration.config import settings
from app.schemas.trending import TrendingBatch, TrendingStatusResponse
from app.trending.orchestrator import trending_orchestrator

logger = logging.getLogger("sielo.api.trending")
router = APIRouter(prefix="/v1/trending", tags=["Trending"])

def verify_admin_key(x_admin_key: Optional[str] = Header(None)):
    if settings.DEBUG or settings.ENVIRONMENT == "development":
        return True
    if not x_admin_key or x_admin_key != settings.ADMIN_API_KEY:
        raise HTTPException(status_code=403, detail="Invalid admin API key")
    return True

@router.get("", response_model=TrendingBatch)
async def get_trending(
    market: str = Query(default="IN", description="Two-letter ISO market code, e.g. IN, US")
):
    """
    Returns the current trending music tracks for the specified market.
    Follows source priority:
    1. YouTube Data API v3
    2. Last.fm API
    3. Sielo Firestore Community Listening
    4. Durable Snapshot
    Served with in-memory caching and stale-while-revalidate behavior.
    """
    clean_market = market.strip().upper()
    return await trending_orchestrator.get_trending(market=clean_market, force_refresh=False)

@router.get("/status", response_model=TrendingStatusResponse)
async def get_trending_status(
    market: str = Query(default="IN", description="Two-letter ISO market code")
):
    """
    Diagnostic endpoint providing live status of all 4 trending providers,
    including health, latencies, last errors, and candidate counts.
    """
    clean_market = market.strip().upper()
    return trending_orchestrator.report_status(market=clean_market)

@router.post("/refresh", response_model=TrendingBatch)
async def force_refresh_trending(
    market: str = Query(default="IN", description="Two-letter ISO market code"),
    _: bool = Depends(verify_admin_key)
):
    """
    Admin endpoint to trigger an immediate live refresh of trending charts
    bypassing the in-memory cache and updating the durable snapshot.
    """
    clean_market = market.strip().upper()
    logger.info(f"Admin triggered manual trending refresh for market={clean_market}")
    return await trending_orchestrator.get_trending(market=clean_market, force_refresh=True)
