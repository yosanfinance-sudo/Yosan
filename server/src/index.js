const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const config = require('./config');

const authRoutes = require('./routes/authRoutes');
const financeRoutes = require('./routes/financeRoutes');
const driveRoutes = require('./routes/driveRoutes');
const chatRoutes = require('./routes/chatRoutes');
const feedbackRoutes = require('./routes/feedbackRoutes');
const statementRoutes = require('./routes/statementRoutes');

const app = express();

// Security Headers (Section 8: Helmet.js)
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", "data:", "https:"],
      connectSrc: ["'self'", "https://generativelanguage.googleapis.com", "https://oauth2.googleapis.com"]
    }
  },
  crossOriginResourcePolicy: { policy: "cross-origin" }
}));

// CORS Configuration (Section 22: Dynamic and Mobile-friendly)
const configuredOrigins = process.env.ALLOWED_ORIGINS
  ? process.env.ALLOWED_ORIGINS.split(',').map(o => o.trim())
  : (config.NODE_ENV === 'production' ? [] : ['http://localhost:3000', 'http://localhost:8080']);

app.use(cors({
  origin: (origin, callback) => {
    // Mobile apps, server-to-server or curl requests have no origin header
    if (!origin) return callback(null, true);
    if (configuredOrigins.length === 0 || configuredOrigins.includes(origin)) {
      return callback(null, true);
    }
    return callback(new Error('CORS policy: Not allowed by origin'));
  },
  credentials: true
}));

app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// Rate limiting (Section 21: Global API limiter)
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 300, // limit each IP to 300 requests per window
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'TOO_MANY_REQUESTS', message: 'Rate limit exceeded, please try again later.' }
});
app.use('/api/', limiter);

// Dedicated stricter rate limiter for Authentication endpoints (Section 21)
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: config.NODE_ENV === 'test' ? 1000 : 30, // 30 requests per 15 min per IP for auth in production
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'TOO_MANY_AUTH_REQUESTS', message: 'Too many authentication attempts, please try again later.' }
});
app.use('/api/auth', authLimiter);

// Root status check
app.get('/', (req, res) => {
  res.json({
    name: 'Yosan Finance API',
    status: 'online',
    healthCheck: '/api/health',
    timestamp: new Date().toISOString()
  });
});

// Health check (Section 9: Do not disclose NODE_ENV in health endpoint)
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Mount API routes
app.use('/api/auth', authRoutes);
app.use('/api/drive', driveRoutes);
app.use('/api/chat', chatRoutes);
app.use('/api/feedback', feedbackRoutes);
app.use('/api/statement', statementRoutes);
app.use('/api', financeRoutes);

// Error Handling (Section 34 - No stack traces exposed to client)
app.use((err, req, res, next) => {
  console.error('[SERVER_ERROR]', err);
  res.status(err.status || 500).json({
    error: err.code || 'INTERNAL_SERVER_ERROR',
    message: err.message || 'An unexpected error occurred'
  });
});

if (require.main === module) {
  app.listen(config.PORT, '0.0.0.0', () => {
    console.log(`Finance Manager Backend listening on http://0.0.0.0:${config.PORT} (${config.NODE_ENV})`);
  });
}

module.exports = app;
