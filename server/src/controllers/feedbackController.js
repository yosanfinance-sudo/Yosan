const feedbackRepository = require('../repositories/feedbackRepository');

const VALID_CATEGORIES = ['BUG_REPORT', 'STATEMENT_PARSING', 'FEATURE_REQUEST', 'GENERAL'];

class FeedbackController {
  async submitFeedback(req, res) {
    try {
      const {
        category,
        rating,
        subject,
        description,
        appVersion,
        deviceModel,
        androidVersion,
        syncId
      } = req.body;

      // 1. Validate Category
      if (!category || !VALID_CATEGORIES.includes(category)) {
        return res.status(400).json({
          error: 'INVALID_CATEGORY',
          message: `Category must be one of: ${VALID_CATEGORIES.join(', ')}`
        });
      }

      // 2. Validate Rating
      const numRating = parseInt(rating, 10);
      if (isNaN(numRating) || numRating < 1 || numRating > 5) {
        return res.status(400).json({
          error: 'INVALID_RATING',
          message: 'Rating must be an integer between 1 and 5'
        });
      }

      // 3. Validate Subject
      if (!subject || typeof subject !== 'string' || subject.trim().length < 3) {
        return res.status(400).json({
          error: 'INVALID_SUBJECT',
          message: 'Subject must be at least 3 characters long'
        });
      }
      if (subject.trim().length > 150) {
        return res.status(400).json({
          error: 'SUBJECT_TOO_LONG',
          message: 'Subject cannot exceed 150 characters'
        });
      }

      // 4. Validate Description
      if (!description || typeof description !== 'string' || description.trim().length < 5) {
        return res.status(400).json({
          error: 'INVALID_DESCRIPTION',
          message: 'Description must be at least 5 characters long'
        });
      }
      if (description.trim().length > 2000) {
        return res.status(400).json({
          error: 'DESCRIPTION_TOO_LONG',
          message: 'Description cannot exceed 2000 characters'
        });
      }

      // 5. Check Idempotency via syncId
      if (syncId) {
        const existing = feedbackRepository.findBySyncId(syncId);
        if (existing) {
          return res.status(200).json({
            success: true,
            feedbackId: existing.id,
            isDuplicate: true,
            message: 'Feedback previously recorded. Thank you!'
          });
        }
      }

      // 6. Record Feedback
      const userId = req.user ? req.user.id : null;
      const result = feedbackRepository.insertFeedback({
        userId,
        category: category.trim(),
        rating: numRating,
        subject: subject.trim(),
        description: description.trim(),
        appVersion: appVersion || 'v1.1.0',
        deviceModel: deviceModel || '',
        androidVersion: androidVersion || '',
        syncId: syncId || null,
        createdAt: Date.now()
      });

      return res.status(201).json({
        success: true,
        feedbackId: result.lastInsertRowid,
        message: 'Feedback submitted successfully. Thank you for your feedback!'
      });
    } catch (err) {
      console.error('[FEEDBACK_ERROR]', err);
      return res.status(500).json({
        error: 'FEEDBACK_SUBMISSION_FAILED',
        message: err.message || 'Failed to submit feedback'
      });
    }
  }

  async getMyFeedbacks(req, res) {
    try {
      if (!req.user || !req.user.id) {
        return res.status(401).json({ error: 'UNAUTHORIZED', message: 'Authentication required' });
      }
      const list = feedbackRepository.getFeedbacksByUser(req.user.id);
      return res.json({ success: true, feedbacks: list });
    } catch (err) {
      return res.status(500).json({ error: 'FAILED_TO_GET_FEEDBACKS', message: err.message });
    }
  }
}

module.exports = new FeedbackController();
