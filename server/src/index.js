const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');
const config = require('./config');

const authRoutes = require('./routes/authRoutes');
const financeRoutes = require('./routes/financeRoutes');
const driveRoutes = require('./routes/driveRoutes');
const chatRoutes = require('./routes/chatRoutes');
const feedbackRoutes = require('./routes/feedbackRoutes');
const statementRoutes = require('./routes/statementRoutes');

const app = express();

// Security & Parsing Middleware
const ALLOWED_ORIGINS = process.env.ALLOWED_ORIGINS
  ? process.env.ALLOWED_ORIGINS.split(',').map(o => o.trim())
  : ['http://localhost:3000', 'http://localhost:8080'];
app.use(cors({ origin: ALLOWED_ORIGINS, credentials: true }));
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// Rate limiting (Section 30)
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 300, // limit each IP to 300 requests per window
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'TOO_MANY_REQUESTS', message: 'Rate limit exceeded, please try again later.' }
});
app.use('/api/', limiter);

// Root status check
app.get('/', (req, res) => {
  res.json({
    name: 'Yosan Finance API',
    status: 'online',
    healthCheck: '/api/health',
    timestamp: new Date().toISOString()
  });
});

// Health check
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString(), env: config.NODE_ENV });
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
