const express = require('express');
const router = express.Router();
const feedbackController = require('../controllers/feedbackController');
const optionalAuthMiddleware = require('../middleware/optionalAuthMiddleware');
const authMiddleware = require('../middleware/authMiddleware');

// Submit feedback (works for logged-in users and guest users with optional auth)
router.post('/', optionalAuthMiddleware, feedbackController.submitFeedback);

// View user's submitted feedbacks (requires auth)
router.get('/my', authMiddleware, feedbackController.getMyFeedbacks);

module.exports = router;
