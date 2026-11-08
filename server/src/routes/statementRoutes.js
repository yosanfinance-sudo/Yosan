const express = require('express');
const router = express.Router();
const rateLimit = require('express-rate-limit');
const config = require('../config');
const statementController = require('../controllers/statementController');
const optionalAuthMiddleware = require('../middleware/optionalAuthMiddleware');

// Dedicated rate limiter for resource-heavy AI statement parsing
const statementAiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: config.NODE_ENV === 'test' ? 1000 : 20, // 20 statement parses per 15 minutes per IP
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'TOO_MANY_REQUESTS', message: 'Too many statement parsing requests, please try again later.' }
});

// Endpoint allows authorized or guest users to parse statement text with rate protection
router.post('/parse-ai', statementAiLimiter, optionalAuthMiddleware, statementController.handleAiParse);

module.exports = router;
