const db = require('../db');

class FeedbackRepository {
  insertFeedback({
    userId = null,
    category,
    rating,
    subject,
    description,
    appVersion = 'v1.1.0',
    deviceModel = '',
    androidVersion = '',
    syncId = null,
    createdAt = Date.now()
  }) {
    const stmt = db.prepare(`
      INSERT INTO feedbacks (user_id, category, rating, subject, description, app_version, device_model, android_version, sync_id, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);
    return stmt.run(
      userId,
      category,
      rating,
      subject,
      description,
      appVersion,
      deviceModel,
      androidVersion,
      syncId,
      createdAt
    );
  }

  findBySyncId(syncId) {
    if (!syncId) return null;
    return db.prepare('SELECT * FROM feedbacks WHERE sync_id = ?').get(syncId);
  }

  getFeedbacksByUser(userId) {
    return db.prepare('SELECT * FROM feedbacks WHERE user_id = ? ORDER BY created_at DESC').all(userId);
  }

  getAllFeedbacks(limit = 100) {
    return db.prepare('SELECT * FROM feedbacks ORDER BY created_at DESC LIMIT ?').all(limit);
  }
}

module.exports = new FeedbackRepository();
