import re
import unicodedata
from typing import Tuple, Dict, Any, List

class TrackNormalizer:
    """
    Normalizes song titles and artist names to create stable canonical identities.
    Deduplicates and aggregates multiple video uploads or metadata variants.
    """

    NOISE_PATTERNS = [
        r"(?i)\s*[\(\[\{]\s*(?:official\s+)?(?:music\s+)?video\s*[\)\]\}]",
        r"(?i)\s*[\(\[\{]\s*(?:official\s+)?audio\s*[\)\]\}]",
        r"(?i)\s*[\(\[\{]\s*(?:official\s+)?lyric(?:s)?(?:\s+video)?\s*[\)\]\}]",
        r"(?i)\s*[\(\[\{]\s*visualizer\s*[\)\]\}]",
        r"(?i)\s*[\(\[\{]\s*full\s+(?:song|video|audio)\s*[\)\]\}]",
        r"(?i)\s*[\(\[\{]\s*hd\s*(?:video)?\s*[\)\]\}]",
        r"(?i)\s*[\(\[\{]\s*4k\s*[\)\]\}]",
        r"(?i)\s*[\(\[\{]\s*remastered\s*[\)\]\}]",
        r"(?i)\s*[\(\[\{]\s*(?:prod\.|produced\s+by)[^\)\]\}]+[\)\]\}]",
        r"(?i)\s*\|\s*.*$",                      # e.g. "Song | Movie | Label"
        r"(?i)\s*//\s*.*$",
    ]

    FEATURE_PATTERNS = [
        r"(?i)\s*[\(\[\{]?\s*(?:feat\.|ft\.|featuring)\s+[^\)\]\}]+[\)\]\}]?",
    ]

    @classmethod
    def clean_text(cls, text: str) -> str:
        """Lowercases, normalizes unicode, and collapses whitespace."""
        if not text:
            return ""
        # Unicode normalization (NFC)
        normalized = unicodedata.normalize("NFC", text)
        # Collapse multiple whitespace
        collapsed = re.sub(r"\s+", " ", normalized).strip()
        return collapsed.lower()

    @classmethod
    def strip_punctuation(cls, text: str) -> str:
        """Removes all non-alphanumeric unicode characters and trims."""
        cleaned = re.sub(r"[^\w\s]", "", text, flags=re.UNICODE)
        return re.sub(r"\s+", " ", cleaned).strip()

    TRAILING_NOISE_PATTERNS = [
        r"(?i)\s*-\s*(?:official\s+)?(?:music\s+)?video.*$",
        r"(?i)\s*-\s*(?:official\s+)?audio.*$",
        r"(?i)\s*-\s*(?:official\s+)?lyric(?:s)?.*$",
        r"(?i)\s*-\s*visualizer.*$",
    ]

    @classmethod
    def clean_title(cls, raw_title: str) -> str:
        """Removes common music video / upload noise annotations from title."""
        title = raw_title.strip()

        # First remove trailing dash annotations like "- Audio" or "- Official Video"
        for pattern in cls.TRAILING_NOISE_PATTERNS:
            title = re.sub(pattern, "", title)

        for pattern in cls.NOISE_PATTERNS:
            title = re.sub(pattern, "", title)

        for pattern in cls.FEATURE_PATTERNS:
            title = re.sub(pattern, "", title)

        # Remove surrounding quotes, dashes, or brackets left behind
        title = title.strip(" -–—:_/|()[]{}'\"")
        return re.sub(r"\s+", " ", title).strip()

    @classmethod
    def extract_artist_and_title(cls, raw_title: str, channel_title: str = "") -> Tuple[str, str]:
        """
        Parses title and artist from raw title and channel info.
        Handles 'Artist - Title' and 'Title - Audio' formats.
        """
        cleaned_channel = re.sub(r"(?i)\s*-\s*topic$", "", channel_title.strip())
        cleaned_channel = re.sub(r"(?i)\s*vevo$", "", cleaned_channel.strip())
        cleaned_channel = re.sub(r"(?i)\s*records$|\s*music$|\s*official$", "", cleaned_channel.strip())

        title = raw_title.strip()
        artist = cleaned_channel

        if " - " in title:
            parts = title.split(" - ", 1)
            first_part = parts[0].strip()
            second_part = parts[1].strip()
            lower_second = second_part.lower()

            # Check if second part is a noise suffix like "Audio", "Official Video", etc.
            if any(lower_second.startswith(w) for w in ["audio", "official", "lyric", "video", "visualizer", "hd", "4k"]):
                title = first_part
            elif len(first_part) <= 50 and not any(first_part.lower().startswith(w) for w in ["audio", "official", "video"]):
                artist = first_part
                title = second_part

        # Clean title of video noise
        title = cls.clean_title(title)

        # Clean artist name
        for pattern in cls.FEATURE_PATTERNS:
            artist = re.sub(pattern, "", artist)
        artist = artist.strip(" -–—:_/|()[]{}'\"")

        if not artist and channel_title:
            artist = cleaned_channel

        return (title or raw_title.strip(), artist or "Unknown Artist")

    @classmethod
    def canonical_key(cls, title: str, artist: str) -> str:
        """
        Generates a normalized comparison key: 'normalized_title:::normalized_artist'.
        """
        norm_title = cls.strip_punctuation(cls.clean_text(cls.clean_title(title)))
        norm_artist = cls.strip_punctuation(cls.clean_text(artist))
        return f"{norm_title}:::{norm_artist}"
