# ==============================================================================
# Production Dockerfile for Yosan Finance Backend (Root Context)
# ==============================================================================
FROM node:20-slim

WORKDIR /app

# Install build dependencies required for better-sqlite3 native compilation
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    make \
    g++ \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install dependencies from server directory
COPY server/package*.json ./
RUN npm install --omit=dev

# Copy server application source code
COPY server/ .

# Ensure data directory exists for SQLite database
RUN mkdir -p /app/data

ENV NODE_ENV=production
ENV PORT=3000
ENV DB_PATH=/app/data/finance_manager.db

EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:3000/api/drive/status || exit 1

CMD ["node", "src/index.js"]
