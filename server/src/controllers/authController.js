const googleAuthService = require('../services/googleAuthService');
const driveBackupService = require('../services/driveBackupService');
const userRepository = require('../repositories/userRepository');
const driveRepository = require('../repositories/driveRepository');
const otpService = require('../services/otpService');
const jwt = require('jsonwebtoken');
const config = require('../config');

class AuthController {
  async sendOtp(req, res) {
    try {
      const { email, purpose } = req.body;
      if (!email) {
        return res.status(400).json({ error: 'MISSING_EMAIL', message: 'Email address is required' });
      }
      const result = await otpService.generateAndSendOtp(email, purpose || 'LOGIN');
      return res.status(200).json(result);
    } catch (err) {
      const isExpected = err.message.startsWith('COOLDOWN_ACTIVE') || err.message.startsWith('INVALID_EMAIL');
      const status = isExpected ? 400 : 500;
      return res.status(status).json({
        error: isExpected ? err.message.split(':')[0] : 'SEND_OTP_FAILED',
        message: err.message
      });
    }
  }

  async verifyOtp(req, res) {
    try {
      const { email, otp, purpose, name } = req.body;
      if (!email || !otp) {
        return res.status(400).json({ error: 'MISSING_CREDENTIALS', message: 'Both email and otp are required' });
      }

      otpService.verifyOtp(email, otp, purpose || 'LOGIN');

      const now = Date.now();
      let user = userRepository.getUserByEmail(email);
      let isNewUser = false;

      if (user) {
        userRepository.updateLastLogin(user.id, now);
        user = userRepository.getUserById(user.id);
      } else {
        isNewUser = true;
        const result = userRepository.createUserWithEmail(email, name, now);
        const userId = result.lastInsertRowid;
        user = userRepository.getUserById(userId);

        userRepository.createUserProfile(userId, now);
        googleAuthService.seedDefaultCategories(userId, now);
      }

      const tokens = googleAuthService.generateTokens(user);
      const expiresAt = now + 30 * 24 * 60 * 60 * 1000;
      userRepository.insertRefreshToken(tokens.refreshToken, user.id, expiresAt);

      const profile = userRepository.getUserProfile(user.id);
      const driveConn = driveRepository.getDriveConnection(user.id);

      return res.status(200).json({
        isNewUser,
        user: {
          id: user.id,
          googleSub: user.google_sub,
          email: user.email,
          name: user.name,
          profilePictureUrl: user.profile_picture_url,
          emailVerified: Boolean(user.email_verified),
          lastLoginAt: user.last_login_at,
          currencySymbol: profile?.currency_symbol || '₹',
          timezone: profile?.timezone || 'Asia/Kolkata',
          theme: profile?.theme || 'SYSTEM',
          isDriveConnected: Boolean(driveConn),
          lastBackupAt: driveConn?.last_backup_at || null
        },
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken
      });
    } catch (err) {
      const isExpected = err.message.startsWith('INVALID_OTP') ||
                         err.message.startsWith('OTP_EXPIRED') ||
                         err.message.startsWith('OTP_NOT_FOUND') ||
                         err.message.startsWith('MAX_ATTEMPTS');
      const status = isExpected ? 400 : 500;
      return res.status(status).json({
        error: isExpected ? err.message.split(':')[0] : 'VERIFY_OTP_FAILED',
        message: err.message
      });
    }
  }

  async requestEmailChange(req, res) {
    try {
      const { newEmail } = req.body;
      if (!newEmail) {
        return res.status(400).json({ error: 'MISSING_NEW_EMAIL', message: 'newEmail parameter is required' });
      }

      const cleanNewEmail = newEmail.trim().toLowerCase();
      const currentUserId = req.user ? req.user.id : null;
      const currentUser = currentUserId ? userRepository.getUserById(currentUserId) : null;

      if (currentUser && currentUser.email && currentUser.email.toLowerCase() === cleanNewEmail) {
        return res.status(400).json({ error: 'SAME_EMAIL', message: 'New email cannot be identical to current email' });
      }

      const existing = userRepository.getUserByEmail(cleanNewEmail);
      if (existing && currentUserId && existing.id !== currentUserId) {
        return res.status(409).json({ error: 'EMAIL_ALREADY_IN_USE', message: 'This email is already associated with another account' });
      }

      const result = await otpService.generateAndSendOtp(cleanNewEmail, 'CHANGE_EMAIL', currentUserId);
      return res.status(200).json(result);
    } catch (err) {
      const isExpected = err.message.startsWith('COOLDOWN_ACTIVE') || err.message.startsWith('INVALID_EMAIL');
      const status = isExpected ? 400 : 500;
      return res.status(status).json({
        error: isExpected ? err.message.split(':')[0] : 'REQUEST_EMAIL_CHANGE_FAILED',
        message: err.message
      });
    }
  }

  async verifyEmailChange(req, res) {
    try {
      const { newEmail, otp } = req.body;
      if (!newEmail || !otp) {
        return res.status(400).json({ error: 'MISSING_CREDENTIALS', message: 'Both newEmail and otp are required' });
      }

      const cleanNewEmail = newEmail.trim().toLowerCase();
      const currentUserId = req.user ? req.user.id : null;

      // Verify OTP strictly for CHANGE_EMAIL purpose
      otpService.verifyOtp(cleanNewEmail, otp, 'CHANGE_EMAIL', currentUserId);

      const existing = userRepository.getUserByEmail(cleanNewEmail);
      if (existing && currentUserId && existing.id !== currentUserId) {
        return res.status(409).json({ error: 'EMAIL_ALREADY_IN_USE', message: 'This email is already associated with another account' });
      }

      const now = Date.now();
      let updatedUser;
      if (currentUserId) {
        userRepository.updateUserEmail(currentUserId, cleanNewEmail, now);
        updatedUser = userRepository.getUserById(currentUserId);
      } else {
        let user = userRepository.getUserByEmail(cleanNewEmail);
        if (!user) {
          const result = userRepository.createUserWithEmail(cleanNewEmail, '', now);
          user = userRepository.getUserById(result.lastInsertRowid);
          userRepository.createUserProfile(user.id, now);
        }
        updatedUser = user;
      }

      const tokens = googleAuthService.generateTokens(updatedUser);
      return res.status(200).json({
        success: true,
        message: 'Email successfully verified and updated',
        email: updatedUser.email,
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken
      });
    } catch (err) {
      const isExpected = err.message.startsWith('INVALID_OTP') ||
                         err.message.startsWith('OTP_EXPIRED') ||
                         err.message.startsWith('OTP_NOT_FOUND') ||
                         err.message.startsWith('MAX_ATTEMPTS') ||
                         err.message.startsWith('UNAUTHORIZED');
      const status = isExpected ? 400 : 500;
      return res.status(status).json({
        error: isExpected ? err.message.split(':')[0] : 'VERIFY_EMAIL_CHANGE_FAILED',
        message: err.message
      });
    }
  }

  async loginGoogle(req, res) {
    try {
      const { idToken } = req.body;
      if (!idToken) {
        return res.status(400).json({ error: 'MISSING_ID_TOKEN', message: 'idToken parameter is required' });
      }

      const authResult = await googleAuthService.handleGoogleAuth(idToken);
      return res.status(200).json(authResult);
    } catch (err) {
      if (err.message === 'INVALID_GOOGLE_TOKEN' || err.message === 'INVALID_GOOGLE_PAYLOAD') {
        return res.status(401).json({ error: 'INVALID_GOOGLE_TOKEN', message: 'Google ID token verification failed' });
      }
      return res.status(500).json({ error: 'AUTH_FAILED', message: err.message || 'Internal server error during authentication' });
    }
  }

  async refreshToken(req, res) {
    const { refreshToken } = req.body;
    if (!refreshToken) {
      return res.status(400).json({ error: 'MISSING_REFRESH_TOKEN', message: 'refreshToken parameter is required' });
    }

    try {
      const storedToken = userRepository.getRefreshToken(refreshToken);
      if (!storedToken || storedToken.expires_at < Date.now()) {
        return res.status(401).json({ error: 'INVALID_REFRESH_TOKEN', message: 'Refresh token is expired or revoked' });
      }

      const decoded = jwt.verify(refreshToken, config.JWT_REFRESH_SECRET);
      const user = userRepository.getActiveUserById(decoded.userId);
      if (!user) {
        return res.status(401).json({ error: 'USER_NOT_FOUND', message: 'Associated user account not found or inactive' });
      }

      // Revoke old token and store new one atomically to prevent race conditions
      const expiresAt = Date.now() + 30 * 24 * 60 * 60 * 1000;
      const newTokens = googleAuthService.generateTokens(user);
      const rotateTokens = require('../db').transaction(() => {
        userRepository.revokeRefreshToken(refreshToken);
        userRepository.insertRefreshToken(newTokens.refreshToken, user.id, expiresAt);
      });
      rotateTokens();

      return res.status(200).json({
        accessToken: newTokens.accessToken,
        refreshToken: newTokens.refreshToken
      });
    } catch (err) {
      return res.status(401).json({ error: 'INVALID_REFRESH_TOKEN', message: 'Refresh token verification failed' });
    }
  }

  async logout(req, res) {
    const { refreshToken } = req.body;
    if (refreshToken) {
      userRepository.revokeRefreshToken(refreshToken);
    }
    return res.status(200).json({ success: true, message: 'Successfully logged out' });
  }

  async getMe(req, res) {
    try {
      const user = userRepository.getUserById(req.user.id);
      const profile = userRepository.getUserProfile(req.user.id);
      const driveConn = driveRepository.getDriveConnection(req.user.id);

      return res.status(200).json({
        user: {
          id: user.id,
          googleSub: user.google_sub,
          email: user.email,
          name: user.name,
          profilePictureUrl: user.profile_picture_url,
          emailVerified: Boolean(user.email_verified),
          createdAt: user.created_at,
          lastLoginAt: user.last_login_at,
          currencySymbol: profile?.currency_symbol || '₹',
          timezone: profile?.timezone || 'Asia/Kolkata',
          theme: profile?.theme || 'SYSTEM',
          isDriveConnected: Boolean(driveConn),
          lastBackupAt: driveConn?.last_backup_at || null
        }
      });
    } catch (err) {
      return res.status(500).json({ error: 'GET_ME_FAILED', message: err.message });
    }
  }

  async deleteAccount(req, res) {
    const userId = req.user.id;
    try {
      await driveBackupService.disconnectDrive(userId);
      userRepository.deleteRefreshTokensByUserId(userId);
      userRepository.deleteUser(userId);
      return res.status(200).json({ success: true, message: 'Account deleted successfully' });
    } catch (err) {
      return res.status(500).json({ error: 'DELETE_ACCOUNT_FAILED', message: err.message });
    }
  }
}

module.exports = new AuthController();
