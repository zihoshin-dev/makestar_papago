# Translation Scripts

## translate_missing_glossary.py

Batch translate missing glossary entries using the DeepL API.

### Prerequisites

1. Install the DeepL Python package:
```bash
pip install deepl
```

2. Get a DeepL API key from https://www.deepl.com/pro-api

### Usage

Set your DeepL API key as an environment variable:
```bash
export DEEPL_API_KEY=your-api-key-here
```

Run the script:
```bash
cd backend/scripts
python translate_missing_glossary.py
```

### Options

- `--dry-run`: Preview what would be translated without making any changes
- `--batch-size N`: Number of texts to translate in one API call (default: 50)

### Example

```bash
# Preview translations without making changes
python translate_missing_glossary.py --dry-run

# Run actual translation
python translate_missing_glossary.py

# Use smaller batch size for more granular error handling
python translate_missing_glossary.py --batch-size 20
```

### What it does

1. Scans `sheet_db.json` for target language columns that still contain Korean text
2. Creates a timestamped backup before making changes
3. Translates missing entries using DeepL API in batches
4. Updates the JSON file with translations
5. Prints a summary of successful and failed translations

### Safety Features

- Creates backup before modifying data
- Continues processing even if some translations fail
- Logs all errors
- Shows detailed progress output
