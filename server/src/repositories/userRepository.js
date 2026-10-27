const db = require('../db');

class UserRepository {
  getUserByGoogleSub(sub) {
    return db.prepare('SELECT * FROM users WHERE google_sub = ?').get(sub);
  }

  getUserByEmail(email) {
    return db.prepare('SELECT * FROM users WHERE lower(email) = lower(?)').get(email);
  }

  getUserById(id) {
    return db.prepare('SELECT * FROM users WHERE id = ?').get(id);
  }

  updateUserEmail(userId, newEmail, now) {
    return db.prepare('UPDATE users SET email = ?, email_verified = 1, updated_at = ? WHERE id = ?').run(newEmail, now, userId);
  }

  getActiveUserById(id) {
    return db.prepare('SELECT * FROM users WHERE id = ? AND is_active = 1').get(id);
  }

  createUser(googleSub, email, name, picture, emailVerified, now) {
    return db.prepare(`
      INSERT INTO users (google_sub, email, name, profile_picture_url, email_verified, created_at, updated_at, last_login_at, is_active)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
    `).run(googleSub, email, name, picture, emailVerified ? 1 : 0, now, now, now);
  }

  createUserWithEmail(email, name, now) {
    const crypto = require('crypto');
    const emailSub = 'email_' + crypto.randomBytes(12).toString('hex');
    return db.prepare(`
      INSERT INTO users (google_sub, email, name, profile_picture_url, email_verified, created_at, updated_at, last_login_at, is_active)
      VALUES (?, ?, ?, '', 1, ?, ?, ?, 1)
    `).run(emailSub, email, name || email.split('@')[0], now, now, now);
  }

  updateLastLogin(userId, now) {
    db.prepare('UPDATE users SET last_login_at = ?, updated_at = ? WHERE id = ?').run(now, now, userId);
  }

  deleteUser(userId) {
    db.prepare('DELETE FROM users WHERE id = ?').run(userId);
  }

  getUserProfile(userId) {
    return db.prepare('SELECT * FROM user_profiles WHERE user_id = ?').get(userId);
  }

  createUserProfile(userId, now) {
    db.prepare(`
      INSERT INTO user_profiles (user_id, currency_symbol, timezone, theme, created_at, updated_at)
      VALUES (?, '₹', 'Asia/Kolkata', 'SYSTEM', ?, ?)
    `).run(userId, now, now);
  }

  getRefreshToken(token) {
    return db.prepare('SELECT * FROM refresh_tokens WHERE token = ? AND revoked = 0').get(token);
  }

  insertRefreshToken(token, userId, expiresAt) {
    db.prepare(`
      INSERT INTO refresh_tokens (token, user_id, expires_at, revoked)
      VALUES (?, ?, ?, 0)
      ON CONFLICT(token) DO UPDATE SET expires_at = excluded.expires_at, revoked = 0
    `).run(token, userId, expiresAt);
  }

  revokeRefreshToken(token) {
    db.prepare('UPDATE refresh_tokens SET revoked = 1 WHERE token = ?').run(token);
  }

  deleteRefreshTokensByUserId(userId) {
    db.prepare('DELETE FROM refresh_tokens WHERE user_id = ?').run(userId);
  }
}

module.exports = new UserRepository();
