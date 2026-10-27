const express = require('express');
const router = express.Router();
const chatController = require('../controllers/chatController');
const authMiddleware = require('../middleware/authMiddleware');
const optionalAuthMiddleware = require('../middleware/optionalAuthMiddleware');

// Accept chat with auth (if token present), or fallback to optional auth
router.post('/', optionalAuthMiddleware, chatController.handleChat);

module.exports = router;
