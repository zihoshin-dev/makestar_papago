#!/usr/bin/env python3
"""
Translate ALL remaining untranslated/empty glossary entries.
- Columns 6 (ES), 7 (ZH-HANT): DeepL -> Claude AI fallback -> English value
- Columns 3 (EN), 4 (ZH-HANS), 5 (JA): DeepL -> Claude AI fallback -> English value
- Empty values in columns 4, 5, 6, 7: fill with English value
"""

import json
import os
import re
import sys
import shutil
import time
import http.client
from datetime import datetime
from pathlib import Path

try:
    import deepl
except ImportError:
    print("ERROR: pip install deepl")
    sys.exit(1)

COL_KEY_NAME = 1
COL_KOREAN = 2
COL_ENGLISH = 3
COL_ZH_HANS = 4
COL_JAPANESE = 5
COL_SPANISH = 6
COL_ZH_HANT = 7

TARGET_COLS = {
    COL_ENGLISH:  {"deepl": "EN-US",   "claude": "English",              "name": "English"},
    COL_ZH_HANS:  {"deepl": "ZH-HANS", "claude": "Simplified Chinese",   "name": "Chinese Simplified"},
    COL_JAPANESE:  {"deepl": "JA",      "claude": "Japanese",             "name": "Japanese"},
    COL_SPANISH:   {"deepl": "ES",      "claude": "Spanish",              "name": "Spanish"},
    COL_ZH_HANT:   {"deepl": "ZH-HANT", "claude": "Traditional Chinese",  "name": "Chinese Traditional"},
}

def has_korean(text):
    if not text or not isinstance(text, str):
        return False
    return bool(re.search(r'[\uAC00-\uD7AF]', text))

def is_empty(text):
    return not text or not isinstance(text, str) or text.strip() == ""

def claude_translate_batch(texts, target_lang_name, api_key):
    if not texts:
        return []
    prompt_texts = "\n".join(f"{i+1}. {t}" for i, t in enumerate(texts))
    body = json.dumps({
        "model": "claude-sonnet-4-20250514",
        "max_tokens": 4096,
        "messages": [{"role": "user", "content": (
            f"Translate the following Korean texts to {target_lang_name}. "
            f"These are K-pop merchandise/product terms for a fan commerce platform. "
            f"For artist names, group names, or brand names that are proper nouns, "
            f"keep them in their commonly known romanized/English form. "
            f"Return ONLY the translations, one per line, numbered to match:\n\n{prompt_texts}"
        )}]
    })
    conn = http.client.HTTPSConnection("api.anthropic.com")
    conn.request("POST", "/v1/messages", body, {
        "Content-Type": "application/json",
        "x-api-key": api_key,
        "anthropic-version": "2023-06-01"
    })
    resp = conn.getresponse()
    data = json.loads(resp.read().decode())
    conn.close()
    if resp.status != 200:
        print(f"  Claude API error: {resp.status} {data.get('error', {}).get('message', '')}")
        return [""] * len(texts)
    response_text = data["content"][0]["text"]
    lines = [l.strip() for l in response_text.strip().split("\n") if l.strip()]
    results = [re.sub(r'^\d+[\.\)]\s*', '', l) for l in lines]
    while len(results) < len(texts):
        results.append("")
    return results[:len(texts)]


def main():
    deepl_key = os.environ.get("DEEPL_API_KEY")
    claude_key = os.environ.get("CLAUDE_API_KEY")
    if not deepl_key:
        print("ERROR: DEEPL_API_KEY not set"); sys.exit(1)
    if not claude_key:
        print("ERROR: CLAUDE_API_KEY not set"); sys.exit(1)

    translator = deepl.Translator(deepl_key)
    usage = translator.get_usage()
    print(f"DeepL connected. Usage: {usage.character.count:,} / {usage.character.limit:,}")

    data_path = Path(__file__).parent.parent / "src" / "main" / "resources" / "data" / "sheet_db.json"
    print(f"Loading: {data_path}")
    with open(data_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = data_path.with_suffix(f".json.backup.{timestamp}")
    shutil.copy2(data_path, backup_path)
    print(f"Backup: {backup_path}\n")

    values = data["values"]

    # Phase A: Fill empty values (cols 4,5,6,7) with English value
    empty_filled = {col: 0 for col in [COL_ZH_HANS, COL_JAPANESE, COL_SPANISH, COL_ZH_HANT]}
    print("Phase A: Filling empty values with English column value...")
    for row_idx in range(2, len(values)):
        row = values[row_idx]
        eng_val = row[COL_ENGLISH] if len(row) > COL_ENGLISH else ""
        if is_empty(eng_val):
            continue
        for col_idx in [COL_ZH_HANS, COL_JAPANESE, COL_SPANISH, COL_ZH_HANT]:
            if col_idx < len(row):
                if is_empty(row[col_idx]):
                    row[col_idx] = eng_val
                    empty_filled[col_idx] += 1
            else:
                while len(row) <= col_idx:
                    row.append("")
                row[col_idx] = eng_val
                empty_filled[col_idx] += 1

    for col_idx, count in empty_filled.items():
        if count > 0:
            print(f"  {TARGET_COLS[col_idx]['name']}: filled {count} empty entries")
    print()

    # Phase B: Find remaining Korean-text entries in ALL columns
    missing = {col: [] for col in TARGET_COLS}
    for row_idx in range(2, len(values)):
        row = values[row_idx]
        for col_idx in TARGET_COLS:
            if col_idx < len(row) and has_korean(row[col_idx]):
                missing[col_idx].append((row_idx, row[col_idx]))

    print("Phase B: Remaining untranslated (Korean text) entries:")
    for col_idx, entries in missing.items():
        if entries:
            print(f"  {TARGET_COLS[col_idx]['name']}: {len(entries)}")

    total_missing = sum(len(v) for v in missing.values())
    if total_missing == 0:
        print("  All entries translated!")
        with open(data_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"\nSaved (empty fills only). Backup: {backup_path}")
        return

    stats = {col: {"deepl": 0, "claude": 0, "english_fallback": 0, "failed": 0} for col in TARGET_COLS}
    BATCH_SIZE = 30

    for col_idx, info in TARGET_COLS.items():
        entries = missing[col_idx]
        if not entries:
            continue

        print(f"\n{'='*50}")
        print(f"Translating {info['name']} ({len(entries)} entries)")
        print(f"{'='*50}")

        # Step 1: DeepL
        deepl_failed = []
        for i in range(0, len(entries), BATCH_SIZE):
            batch = entries[i:i + BATCH_SIZE]
            texts = [t for _, t in batch]
            row_indices = [r for r, _ in batch]
            bn = i // BATCH_SIZE + 1
            tb = (len(entries) + BATCH_SIZE - 1) // BATCH_SIZE
            print(f"  DeepL {bn}/{tb} ({len(texts)} texts)...", end=" ")
            try:
                results = translator.translate_text(texts, source_lang="KO", target_lang=info["deepl"])
                if not isinstance(results, list):
                    results = [results]
                ok = 0
                for j, result in enumerate(results):
                    translated = result.text
                    if translated and not has_korean(translated):
                        data["values"][row_indices[j]][col_idx] = translated
                        stats[col_idx]["deepl"] += 1
                        ok += 1
                    else:
                        deepl_failed.append((row_indices[j], texts[j]))
                print(f"OK ({ok}/{len(batch)})")
            except Exception as e:
                print(f"FAILED: {e}")
                deepl_failed.extend(batch)

        # Step 2: Claude AI fallback
        if deepl_failed:
            print(f"\n  Claude fallback: {len(deepl_failed)} entries...")
            claude_failed = []
            for i in range(0, len(deepl_failed), 20):
                batch = deepl_failed[i:i + 20]
                texts = [t for _, t in batch]
                row_indices = [r for r, _ in batch]
                bn = i // 20 + 1
                tb = (len(deepl_failed) + 19) // 20
                print(f"  Claude {bn}/{tb} ({len(texts)} texts)...", end=" ")
                try:
                    translations = claude_translate_batch(texts, info["claude"], claude_key)
                    ok = 0
                    for j, translation in enumerate(translations):
                        if translation and not has_korean(translation):
                            data["values"][row_indices[j]][col_idx] = translation
                            stats[col_idx]["claude"] += 1
                            ok += 1
                        else:
                            claude_failed.append((row_indices[j], texts[j]))
                    print(f"OK ({ok}/{len(batch)})")
                except Exception as e:
                    print(f"FAILED: {e}")
                    claude_failed.extend(batch)
                time.sleep(1)

            # Step 3: English fallback (for cols 4,5,6,7 only; col 3 IS English)
            if claude_failed:
                print(f"\n  English fallback: {len(claude_failed)} proper nouns...")
                for row_idx, korean_text in claude_failed:
                    row = data["values"][row_idx]
                    if col_idx == COL_ENGLISH:
                        # For English column, can't fallback to itself
                        stats[col_idx]["failed"] += 1
                        key_name = row[COL_KEY_NAME] if len(row) > COL_KEY_NAME else "?"
                        print(f"    FAILED: row {row_idx} ({key_name}): {korean_text[:40]}")
                    else:
                        eng_val = row[COL_ENGLISH] if len(row) > COL_ENGLISH else ""
                        if eng_val and not has_korean(eng_val):
                            data["values"][row_idx][col_idx] = eng_val
                            stats[col_idx]["english_fallback"] += 1
                        else:
                            stats[col_idx]["failed"] += 1
                            key_name = row[COL_KEY_NAME] if len(row) > COL_KEY_NAME else "?"
                            print(f"    FAILED: row {row_idx} ({key_name}): {korean_text[:40]}")

    # Save
    print(f"\nSaving to: {data_path}")
    with open(data_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    # Summary
    print(f"\n{'='*60}")
    print("TRANSLATION SUMMARY")
    print(f"{'='*60}")

    print("\nPhase A - Empty values filled with English:")
    for col_idx, count in empty_filled.items():
        if count > 0:
            print(f"  {TARGET_COLS[col_idx]['name']}: {count}")

    print("\nPhase B - Korean text translated:")
    grand_success = 0
    grand_failed = 0
    for col_idx, info in TARGET_COLS.items():
        s = stats[col_idx]
        total = s["deepl"] + s["claude"] + s["english_fallback"] + s["failed"]
        if total > 0:
            print(f"\n  {info['name']} ({total} entries):")
            print(f"    DeepL:            {s['deepl']}")
            print(f"    Claude AI:        {s['claude']}")
            print(f"    English fallback: {s['english_fallback']}")
            print(f"    Failed:           {s['failed']}")
            grand_success += s["deepl"] + s["claude"] + s["english_fallback"]
            grand_failed += s["failed"]

    print(f"\nGrand Total: {grand_success} success, {grand_failed} failed")
    print(f"Backup: {backup_path}")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
