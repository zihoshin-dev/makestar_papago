#!/usr/bin/env python3
"""
Batch translate missing glossary entries using DeepL API.

This script identifies glossary entries where target language columns still contain
Korean text (Hangul characters) and translates them using the DeepL API.

PREREQUISITES:
    pip install deepl

USAGE:
    export DEEPL_API_KEY=your-api-key-here
    python translate_missing_glossary.py

    Optional arguments:
        --dry-run    : Show what would be translated without making changes
        --batch-size : Number of texts to translate in one API call (default: 50)

BEHAVIOR:
    - Creates a backup file before modifying: sheet_db.json.backup.TIMESTAMP
    - Processes all rows starting from index 2 (after header rows)
    - Detects Korean text in target language columns using Unicode range check
    - Translates in batches for efficiency
    - Shows progress and summary statistics
    - Logs errors for failed translations (continues processing)
"""

import json
import os
import re
import sys
import shutil
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Tuple
import argparse

try:
    import deepl
except ImportError:
    print("ERROR: The 'deepl' package is not installed.")
    print("Please install it with: pip install deepl")
    sys.exit(1)


# Column indices in the values array
COL_PAGE_URL = 0
COL_KEY_NAME = 1
COL_KOREAN = 2
COL_ENGLISH = 3
COL_ZH_HANS = 4
COL_JAPANESE = 5
COL_SPANISH = 6
COL_ZH_HANT = 7

# DeepL target language codes
DEEPL_LANG_MAP = {
    COL_ENGLISH: "EN-US",
    COL_ZH_HANS: "ZH-HANS",
    COL_JAPANESE: "JA",
    COL_SPANISH: "ES",
    COL_ZH_HANT: "ZH-HANT",
}

# Language names for logging
LANG_NAMES = {
    COL_ENGLISH: "English",
    COL_ZH_HANS: "Chinese Simplified",
    COL_JAPANESE: "Japanese",
    COL_SPANISH: "Spanish",
    COL_ZH_HANT: "Chinese Traditional",
}


def has_korean(text: str) -> bool:
    """Check if text contains Korean Hangul characters."""
    if not text or not isinstance(text, str):
        return False
    return bool(re.search(r'[\uAC00-\uD7AF]', text))


def find_missing_translations(data: Dict) -> Dict[int, List[Tuple[int, str]]]:
    """
    Find all entries where target columns contain Korean text.

    Returns:
        Dict mapping row_index to list of (col_index, korean_text) tuples
    """
    missing = {}
    values = data.get("values", [])

    # Start from index 2 (skip header rows)
    for row_idx in range(2, len(values)):
        row = values[row_idx]
        row_missing = []

        # Check each target language column
        for col_idx in DEEPL_LANG_MAP.keys():
            if col_idx < len(row):
                text = row[col_idx]
                if has_korean(text):
                    row_missing.append((col_idx, text))

        if row_missing:
            missing[row_idx] = row_missing

    return missing


def create_backup(file_path: Path) -> Path:
    """Create a timestamped backup of the original file."""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = file_path.with_suffix(f".json.backup.{timestamp}")
    shutil.copy2(file_path, backup_path)
    return backup_path


def translate_batch(translator: deepl.Translator, texts: List[str], target_lang: str) -> List[str]:
    """
    Translate a batch of texts to the target language.

    Returns:
        List of translated texts (same order as input)
    """
    try:
        results = translator.translate_text(
            texts,
            source_lang="KO",
            target_lang=target_lang
        )

        # Handle both single result and list of results
        if isinstance(results, list):
            return [r.text for r in results]
        else:
            return [results.text]
    except Exception as e:
        print(f"  ERROR: Translation failed: {e}")
        return [""] * len(texts)


def main():
    parser = argparse.ArgumentParser(description="Translate missing glossary entries using DeepL API")
    parser.add_argument("--dry-run", action="store_true", help="Show what would be translated without making changes")
    parser.add_argument("--batch-size", type=int, default=50, help="Number of texts to translate in one API call")
    args = parser.parse_args()

    # Check for API key
    api_key = os.environ.get("DEEPL_API_KEY")
    if not api_key:
        print("ERROR: DEEPL_API_KEY environment variable is not set.")
        print("Please set it with: export DEEPL_API_KEY=your-api-key-here")
        sys.exit(1)

    # Initialize DeepL translator
    try:
        translator = deepl.Translator(api_key)
        # Test the connection
        usage = translator.get_usage()
        print(f"DeepL API connected successfully.")
        print(f"Character usage: {usage.character.count:,} / {usage.character.limit:,}")
        print()
    except Exception as e:
        print(f"ERROR: Failed to connect to DeepL API: {e}")
        sys.exit(1)

    # Load glossary data
    data_path = Path(__file__).parent.parent / "src" / "main" / "resources" / "data" / "sheet_db.json"

    if not data_path.exists():
        print(f"ERROR: Data file not found: {data_path}")
        sys.exit(1)

    print(f"Loading glossary data from: {data_path}")
    with open(data_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    total_rows = len(data.get("values", [])) - 2  # Exclude header rows
    print(f"Total data rows: {total_rows}")
    print()

    # Find missing translations
    print("Scanning for missing translations...")
    missing = find_missing_translations(data)

    if not missing:
        print("No missing translations found. All entries are complete!")
        return

    # Count by language
    lang_counts = {col: 0 for col in DEEPL_LANG_MAP.keys()}
    for row_missing in missing.values():
        for col_idx, _ in row_missing:
            lang_counts[col_idx] += 1

    print(f"Found {len(missing)} rows with missing translations:")
    for col_idx, count in lang_counts.items():
        if count > 0:
            print(f"  - {LANG_NAMES[col_idx]}: {count} entries")
    print()

    if args.dry_run:
        print("DRY RUN MODE - No changes will be made")
        print("\nSample entries that would be translated:")
        for row_idx in list(missing.keys())[:5]:
            row = data["values"][row_idx]
            key_name = row[COL_KEY_NAME] if len(row) > COL_KEY_NAME else ""
            korean_text = row[COL_KOREAN] if len(row) > COL_KOREAN else ""
            print(f"  Row {row_idx}: {key_name}")
            print(f"    Korean: {korean_text[:50]}...")
            for col_idx, text in missing[row_idx]:
                print(f"    {LANG_NAMES[col_idx]}: {text[:50]}...")
        return

    # Create backup
    backup_path = create_backup(data_path)
    print(f"Created backup: {backup_path}")
    print()

    # Process translations by language (more efficient batching)
    translation_stats = {col: {"success": 0, "failed": 0} for col in DEEPL_LANG_MAP.keys()}
    failed_entries = []

    for col_idx, target_lang in DEEPL_LANG_MAP.items():
        if lang_counts[col_idx] == 0:
            continue

        print(f"Translating {LANG_NAMES[col_idx]} ({lang_counts[col_idx]} entries)...")

        # Collect all texts for this language
        batch_data = []
        for row_idx, row_missing in missing.items():
            for col, text in row_missing:
                if col == col_idx:
                    batch_data.append((row_idx, text))

        # Process in batches
        for i in range(0, len(batch_data), args.batch_size):
            batch = batch_data[i:i + args.batch_size]
            texts = [text for _, text in batch]
            row_indices = [row_idx for row_idx, _ in batch]

            print(f"  Batch {i // args.batch_size + 1}/{(len(batch_data) + args.batch_size - 1) // args.batch_size} ({len(texts)} texts)...", end=" ")

            try:
                translations = translate_batch(translator, texts, target_lang)

                # Update the data
                for j, (row_idx, translation) in enumerate(zip(row_indices, translations)):
                    if translation:
                        data["values"][row_idx][col_idx] = translation
                        translation_stats[col_idx]["success"] += 1
                    else:
                        translation_stats[col_idx]["failed"] += 1
                        failed_entries.append((row_idx, col_idx, texts[j]))

                print("Done")
            except Exception as e:
                print(f"FAILED: {e}")
                translation_stats[col_idx]["failed"] += len(texts)
                for row_idx, text in batch:
                    failed_entries.append((row_idx, col_idx, text))

        print()

    # Save updated data
    print(f"Saving updated data to: {data_path}")
    with open(data_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    # Print summary
    print("\n" + "=" * 60)
    print("TRANSLATION SUMMARY")
    print("=" * 60)

    total_success = 0
    total_failed = 0

    for col_idx in DEEPL_LANG_MAP.keys():
        success = translation_stats[col_idx]["success"]
        failed = translation_stats[col_idx]["failed"]
        total_success += success
        total_failed += failed

        if success + failed > 0:
            print(f"\n{LANG_NAMES[col_idx]}:")
            print(f"  Success: {success}")
            print(f"  Failed:  {failed}")

    print(f"\nTotal:")
    print(f"  Success: {total_success}")
    print(f"  Failed:  {total_failed}")

    if failed_entries:
        print(f"\nFailed entries ({len(failed_entries)} total):")
        for row_idx, col_idx, text in failed_entries[:10]:
            row = data["values"][row_idx]
            key_name = row[COL_KEY_NAME] if len(row) > COL_KEY_NAME else ""
            print(f"  Row {row_idx} ({key_name}), {LANG_NAMES[col_idx]}: {text[:50]}...")
        if len(failed_entries) > 10:
            print(f"  ... and {len(failed_entries) - 10} more")

    print(f"\nBackup file: {backup_path}")
    print("=" * 60)


if __name__ == "__main__":
    main()
