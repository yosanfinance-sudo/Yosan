const crypto = require('crypto');
const db = require('../db');
const config = require('../config');
const emailService = require('./emailService');

const COOLDOWN_MS = 60 * 1000; // 60 seconds cooldown
const EXPIRES_IN_MS = 10 * 60 * 1000; // 10 minutes expiry
const MAX_ATTEMPTS = 5;

function hashOtp(otp) {
  const secret = config.JWT_SECRET || 'dev_secret_finance_manager_2026';
  return crypto.createHash('sha256').update(otp + secret).digest('hex');
}

function isValidEmail(email) {
  if (!email || typeof email !== 'string') return false;
  const regex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return regex.test(email.trim());
}

class OtpService {
  async generateAndSendOtp(email, purpose = 'LOGIN', userId = null) {
    const cleanEmail = (email || '').trim().toLowerCase();
    if (!isValidEmail(cleanEmail)) {
      throw new Error('INVALID_EMAIL_FORMAT: Please provide a valid email address.');
    }

    const now = Date.now();

    // Check cooldown
    const latest = db.prepare(`
      SELECT created_at FROM email_verifications
      WHERE lower(email) = ? AND purpose = ?
      ORDER BY id DESC LIMIT 1
    `).get(cleanEmail, purpose);

    if (latest && (now - latest.created_at) < COOLDOWN_MS) {
      const remainingSeconds = Math.ceil((COOLDOWN_MS - (now - latest.created_at)) / 1000);
      throw new Error(`COOLDOWN_ACTIVE: Please wait ${remainingSeconds} seconds before requesting a new code.`);
    }

    // Invalidate existing active OTPs for this email and purpose
    db.prepare(`
      UPDATE email_verifications SET verified = 2
      WHERE lower(email) = ? AND purpose = ? AND verified = 0
    `).run(cleanEmail, purpose);

    // Generate 6-digit numeric OTP
    const otp = crypto.randomInt(100000, 1000000).toString();
    const otpHash = hashOtp(otp);
    const expiresAt = now + EXPIRES_IN_MS;

    db.prepare(`
      INSERT INTO email_verifications (email, otp_hash, purpose, user_id, attempts, expires_at, verified, created_at)
      VALUES (?, ?, ?, ?, 0, ?, 0, ?)
    `).run(cleanEmail, otpHash, purpose, userId, expiresAt, now);

    // Send email asynchronously in the background so response to app is instant (<100ms)
    emailService.sendOtpEmail(cleanEmail, otp, purpose).catch(err => {
      console.error('[OTP_SERVICE] Background email delivery failed:', err && err.message ? err.message : err);
    });

    return {
      success: true,
      message: `Verification code sent to ${cleanEmail}`,
      cooldownSeconds: 60,
      expiresInSeconds: 600,
      ...(config.NODE_ENV === 'test' ? { devOtp: otp } : {})
    };
  }

  verifyOtp(email, otp, purpose = 'LOGIN', userId = null) {
    const cleanEmail = (email || '').trim().toLowerCase();
    const cleanOtp = (otp || '').trim();

    if (!isValidEmail(cleanEmail)) {
      throw new Error('INVALID_EMAIL_FORMAT: Please provide a valid email address.');
    }

    if (!cleanOtp || cleanOtp.length !== 6 || !/^\d{6}$/.test(cleanOtp)) {
      throw new Error('INVALID_OTP_FORMAT: Verification code must be exactly 6 digits.');
    }

    const now = Date.now();

    const record = db.prepare(`
      SELECT * FROM email_verifications
      WHERE lower(email) = ? AND purpose = ? AND verified = 0
      ORDER BY id DESC LIMIT 1
    `).get(cleanEmail, purpose);

    if (!record) {
      throw new Error('OTP_NOT_FOUND: No active verification code found for this email. Please request a new code.');
    }

    if (record.expires_at < now) {
      throw new Error('OTP_EXPIRED: Verification code has expired. Please request a new code.');
    }

    if (record.attempts >= MAX_ATTEMPTS) {
      throw new Error('MAX_ATTEMPTS_EXCEEDED: Too many incorrect attempts. Please request a new code.');
    }

    if (userId && record.user_id && record.user_id !== userId) {
      throw new Error('UNAUTHORIZED_USER: Verification code does not match this user session.');
    }

    const inputHash = hashOtp(cleanOtp);
    if (inputHash !== record.otp_hash) {
      const newAttempts = record.attempts + 1;
      db.prepare('UPDATE email_verifications SET attempts = ? WHERE id = ?').run(newAttempts, record.id);
      const remainingAttempts = Math.max(0, MAX_ATTEMPTS - newAttempts);
      throw new Error(`INVALID_OTP: Incorrect code. ${remainingAttempts} attempts remaining.`);
    }

    // Success: mark verified
    db.prepare('UPDATE email_verifications SET verified = 1 WHERE id = ?').run(record.id);

    return {
      success: true,
      email: cleanEmail,
      purpose
    };
  }
}

module.exports = new OtpService();
