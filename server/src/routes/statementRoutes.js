const express = require('express');
const router = express.Router();
const statementController = require('../controllers/statementController');
const optionalAuthMiddleware = require('../middleware/optionalAuthMiddleware');

// Endpoint allows authorized or guest users to parse statement text
router.post('/parse-ai', optionalAuthMiddleware, statementController.handleAiParse);

module.exports = router;
