# ==============================================================================
# Production Dockerfile for Yosan Finance Backend
# ==============================================================================
# Stage 1: Build native dependencies
FROM node:20-slim AS builder

WORKDIR /app

# Install build dependencies required for better-sqlite3 native compilation
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    make \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# Install dependencies
COPY package*.json ./
RUN npm install --omit=dev

# Stage 2: Clean production runtime image (no build compilers, running as non-root user)
FROM node:20-slim AS runner

WORKDIR /app

# Install curl for container HEALTHCHECK
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy prebuilt node_modules from builder (Section 7)
COPY --from=builder /app/node_modules ./node_modules
COPY package*.json ./

# Copy server application source code
COPY . .

# Ensure data directory exists for SQLite database and is owned by 'node' user
RUN mkdir -p /app/data && chown -R node:node /app

# Run as non-root user (Section 6)
USER node

ENV NODE_ENV=production
ENV PORT=3000
ENV DB_PATH=/app/data/finance_manager.db

EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:3000/api/health || exit 1

CMD ["node", "src/index.js"]
