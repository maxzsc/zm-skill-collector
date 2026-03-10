#!/usr/bin/env bash
#
# build-plugin.sh — Fetch all skills from the server and populate the skills/ directory
# so the plugin is ready for distribution.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SKILLS_DIR="$PROJECT_DIR/skills"

# Determine server URL: env > plugin.json config > default
if [ -n "${ZM_SKILL_SERVER:-}" ]; then
  SERVER_URL="$ZM_SKILL_SERVER"
elif command -v jq &>/dev/null && [ -f "$PROJECT_DIR/plugin.json" ]; then
  SERVER_URL=$(jq -r '.config.server_url // "http://localhost:8080"' "$PROJECT_DIR/plugin.json")
else
  SERVER_URL="http://localhost:8080"
fi

# Strip trailing slash
SERVER_URL="${SERVER_URL%/}"

echo "==> Server: $SERVER_URL"
echo "==> Skills directory: $SKILLS_DIR"

# Ensure skills directory exists and is clean
mkdir -p "$SKILLS_DIR"
rm -f "$SKILLS_DIR"/*.md

# Step 1: Fetch skill index
echo "==> Fetching skill index..."
INDEX_RESPONSE=$(curl -sf "${SERVER_URL}/api/skills/index") || {
  echo "ERROR: Failed to fetch skill index from ${SERVER_URL}/api/skills/index"
  exit 1
}

# Extract skill names from JSON array
if ! command -v jq &>/dev/null; then
  echo "ERROR: jq is required but not installed. Install it with: brew install jq"
  exit 1
fi

SKILL_NAMES=$(echo "$INDEX_RESPONSE" | jq -r '.[].name')

if [ -z "$SKILL_NAMES" ]; then
  echo "==> No skills found on server."
  exit 0
fi

SKILL_COUNT=0

# Step 2: Fetch each skill and write to skills/ directory
while IFS= read -r SKILL_NAME; do
  [ -z "$SKILL_NAME" ] && continue

  echo "    Fetching: $SKILL_NAME"

  # URL-encode the skill name (basic: replace spaces with %20)
  ENCODED_NAME=$(echo "$SKILL_NAME" | sed 's/ /%20/g')

  SKILL_CONTENT=$(curl -sf "${SERVER_URL}/api/skills/${ENCODED_NAME}") || {
    echo "    WARNING: Failed to fetch skill '${SKILL_NAME}', skipping."
    continue
  }

  # Write skill content as markdown file
  # Use the name field for the filename, sanitizing special characters
  SAFE_NAME=$(echo "$SKILL_NAME" | sed 's/[^a-zA-Z0-9_\u4e00-\u9fff-]/_/g')

  # Extract content from JSON response and write as .md
  echo "$SKILL_CONTENT" | jq -r '.content // empty' > "$SKILLS_DIR/${SAFE_NAME}.md" 2>/dev/null || {
    # If jq extraction fails, write the raw JSON
    echo "$SKILL_CONTENT" > "$SKILLS_DIR/${SAFE_NAME}.json"
  }

  SKILL_COUNT=$((SKILL_COUNT + 1))
done <<< "$SKILL_NAMES"

echo ""
echo "==> Done! $SKILL_COUNT skills written to $SKILLS_DIR"
echo "==> Plugin is ready for distribution."
