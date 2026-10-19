import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.configuration.config import settings
from app.api.routes_recommendations import router as rec_router
from app.api.routes_events import router as events_router
from app.api.routes_admin import router as admin_router
from app.api.routes_trending import router as trending_router
from app.training.scheduler import start_scheduler, stop_scheduler

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("sielo.main")

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Initializing Sielo Recommendation Engine & Trending System...")
    start_scheduler()
    yield
    logger.info("Shutting down Sielo Recommendation Engine & Trending System...")
    stop_scheduler()

app = FastAPI(
    title=settings.APP_NAME,
    version="1.0.0",
    description="Production Hybrid Recommendation Engine for Sielo Music (Android)",
    lifespan=lifespan
)

# CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount Routers
app.include_router(rec_router)
app.include_router(events_router)
app.include_router(admin_router)
app.include_router(trending_router)
# Include alias for /api/trending compatibility
app.include_router(trending_router, prefix="/api", tags=["Trending Alias"])

@app.get("/health", tags=["Health"])
async def health_check():
    return {
        "status": "healthy",
        "service": settings.APP_NAME,
        "environment": settings.ENVIRONMENT
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
