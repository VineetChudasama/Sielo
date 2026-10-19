import os
import logging
from typing import Optional
from fastapi import Header, HTTPException, Depends, status
import firebase_admin
from firebase_admin import credentials, auth
from app.configuration.config import settings

logger = logging.getLogger("sielo.auth")

_firebase_initialized = False

def init_firebase():
    global _firebase_initialized
    if _firebase_initialized:
        return
    try:
        cred_path = settings.FIREBASE_CREDENTIALS_PATH
        if cred_path and os.path.exists(cred_path):
            cred = credentials.Certificate(cred_path)
            firebase_admin.initialize_app(cred)
            logger.info(f"Firebase Admin initialized with credentials from {cred_path}")
        else:
            # Attempt default application credentials (GCP Cloud Run / Compute Engine)
            try:
                firebase_admin.initialize_app()
                logger.info("Firebase Admin initialized with default credentials")
            except Exception as e:
                logger.warning(f"Firebase credentials not supplied. Mock/dev auth enabled: {e}")
        _firebase_initialized = True
    except Exception as e:
        logger.warning(f"Could not initialize Firebase Admin SDK: {e}")

async def get_current_user(authorization: Optional[str] = Header(None)) -> str:
    """
    Validates Firebase Bearer token and returns the authenticated user's UID.
    In testing or dev environments with ALLOW_MOCK_AUTH_FOR_TESTS, handles dev tokens safely.
    """
    if not authorization:
        # Fall back to anonymous guest if allowed, or reject
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing Authorization header"
        )
    
    parts = authorization.split()
    if len(parts) != 2 or parts[0].lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid Authorization header format. Expected 'Bearer <token>'"
        )
    
    token = parts[1]
    
    # Development / Testing token bypass
    if settings.ALLOW_MOCK_AUTH_FOR_TESTS and (token.startswith("test_user_") or token.startswith("mock_") or token == "dev_token"):
        return token.replace("test_user_", "user_").replace("mock_", "user_")
    
    init_firebase()
    
    try:
        decoded_token = auth.verify_id_token(token)
        uid = decoded_token.get("uid")
        if not uid:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token: missing uid")
        return uid
    except Exception as e:
        logger.error(f"Token verification failed: {e}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication failed: invalid or expired token"
        )

async def verify_admin_key(x_admin_key: Optional[str] = Header(None)) -> bool:
    if not x_admin_key or x_admin_key != settings.ADMIN_API_KEY:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Invalid or missing admin credentials"
        )
    return True
