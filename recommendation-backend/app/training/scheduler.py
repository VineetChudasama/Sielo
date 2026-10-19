import logging
from apscheduler.schedulers.background import BackgroundScheduler
from app.configuration.config import settings
from app.training.pipeline import training_pipeline
from app.data.firestore_client import firestore_client

logger = logging.getLogger("sielo.scheduler")

_scheduler = None
_last_event_count = 0

def check_and_retrain():
    global _last_event_count
    total_events = firestore_client.count_total_events()
    new_events = total_events - _last_event_count

    if new_events >= settings.MIN_NEW_EVENTS_TO_RETRAIN:
        logger.info(f"Sufficient new events ({new_events} >= {settings.MIN_NEW_EVENTS_TO_RETRAIN}). Starting scheduled retraining.")
        res = training_pipeline.run_training_job(trigger_source="scheduler")
        if res.status == "promoted":
            _last_event_count = total_events
    else:
        logger.info(f"Scheduled retrain skipped: only {new_events} new events (threshold: {settings.MIN_NEW_EVENTS_TO_RETRAIN}).")

def start_scheduler():
    global _scheduler
    if _scheduler is not None and _scheduler.running:
        return

    _scheduler = BackgroundScheduler(daemon=True)
    _scheduler.add_job(
        check_and_retrain,
        'interval',
        hours=settings.RETRAIN_INTERVAL_HOURS,
        id="model_retraining_job",
        replace_existing=True
    )
    _scheduler.start()
    logger.info(f"Recommendation retraining scheduler started (interval: {settings.RETRAIN_INTERVAL_HOURS} hours).")

def stop_scheduler():
    global _scheduler
    if _scheduler is not None and _scheduler.running:
        _scheduler.shutdown(wait=False)
        logger.info("Retraining scheduler stopped.")
