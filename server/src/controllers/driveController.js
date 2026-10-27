const driveBackupService = require('../services/driveBackupService');
const userRepository = require('../repositories/userRepository');

function resolveUserId(req) {
  if (req.user && req.user.id) return req.user.id;
  const email = (req.body && req.body.driveEmail) || req.headers['x-user-email'] || (req.query && req.query.email);
  if (email) {
    const cleanEmail = email.trim().toLowerCase();
    const user = userRepository.getUserByEmail(cleanEmail);
    if (user) return user.id;
    const res = userRepository.createUserWithEmail(cleanEmail, '', Date.now());
    userRepository.createUserProfile(res.lastInsertRowid, Date.now());
    return res.lastInsertRowid;
  }
  return null;
}

class DriveController {
  async connectDrive(req, res) {
    const { authCode, refreshToken, googleSub, driveEmail } = req.body;
    try {
      const userId = resolveUserId(req);
      if (!userId) {
        return res.status(400).json({
          error: 'EMAIL_REQUIRED',
          message: 'User account or email is required to connect Google Drive'
        });
      }
      const result = await driveBackupService.connectDrive(
        userId,
        authCode || refreshToken,
        googleSub || (req.user && req.user.googleSub),
        driveEmail
      );
      res.json(result);
    } catch (err) {
      const isMismatch = err.message.startsWith('EMAIL_MISMATCH');
      res.status(400).json({
        error: isMismatch ? 'EMAIL_MISMATCH' : 'DRIVE_CONNECT_FAILED',
        message: err.message
      });
    }
  }

  async getStatus(req, res) {
    try {
      const userId = resolveUserId(req);
      if (!userId) {
        return res.json({ isConnected: false, driveEmail: null });
      }
      const status = driveBackupService.getDriveStatus(userId);
      res.json(status);
    } catch (err) {
      res.status(500).json({ error: 'DRIVE_STATUS_FAILED', message: err.message });
    }
  }

  async disconnectDrive(req, res) {
    try {
      const userId = resolveUserId(req);
      if (!userId) {
        return res.json({ success: true, message: 'Google Drive disconnected' });
      }
      const result = await driveBackupService.disconnectDrive(userId);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'DRIVE_DISCONNECT_FAILED', message: err.message });
    }
  }

  async backupNow(req, res) {
    try {
      const userId = resolveUserId(req);
      if (!userId) {
        return res.status(400).json({ error: 'USER_REQUIRED', message: 'User account could not be identified' });
      }
      const clientPayload = req.body && (req.body.payload || (req.body.accounts || req.body.transactions ? req.body : null));
      const result = await driveBackupService.uploadBackup(userId, clientPayload);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'BACKUP_FAILED', message: err.message });
    }
  }

  async listBackups(req, res) {
    try {
      const userId = resolveUserId(req);
      if (!userId) return res.json({ backups: [] });
      const backups = await driveBackupService.listBackups(userId);
      res.json({ backups });
    } catch (err) {
      res.status(500).json({ error: 'LIST_BACKUPS_FAILED', message: err.message });
    }
  }

  async getBackupVersion(req, res) {
    try {
      const userId = resolveUserId(req);
      if (!userId) return res.status(400).json({ error: 'USER_REQUIRED' });
      const version = req.params.version || req.query.version;
      const data = await driveBackupService.getBackupVersionPayload(userId, version);
      res.json({
        success: true,
        version: data.backup_version || parseInt(version, 10) || 1,
        backupJson: JSON.stringify(data),
        backupData: data
      });
    } catch (err) {
      res.status(404).json({ error: 'VERSION_NOT_FOUND', message: err.message });
    }
  }

  async restoreBackupVersion(req, res) {
    const { version, confirm } = req.body;
    if (!confirm) {
      return res.status(400).json({ error: 'CONFIRMATION_REQUIRED', message: 'Explicit confirmation required before restoring backup' });
    }

    try {
      const userId = resolveUserId(req);
      if (!userId) return res.status(400).json({ error: 'USER_REQUIRED' });
      const versionData = await driveBackupService.getBackupVersionPayload(userId, version);
      const result = driveBackupService.restoreBackupData(userId, versionData);
      res.json({
        success: true,
        version: versionData.backup_version || version,
        message: `Version ${version} restored successfully`,
        details: result
      });
    } catch (err) {
      res.status(400).json({ error: 'RESTORE_VERSION_FAILED', message: err.message });
    }
  }

  async restoreBackup(req, res) {
    const { backupData, confirm } = req.body;
    if (!confirm) {
      return res.status(400).json({ error: 'CONFIRMATION_REQUIRED', message: 'Explicit confirmation required before restoring backup' });
    }

    try {
      if (!backupData) {
        return res.status(400).json({ error: 'BACKUP_DATA_REQUIRED', message: 'backupData is required in the request body' });
      }
      const userId = resolveUserId(req);
      if (!userId) {
        return res.status(400).json({ error: 'USER_REQUIRED', message: 'User account could not be identified' });
      }
      const result = driveBackupService.restoreBackupData(userId, backupData);
      res.json(result);
    } catch (err) {
      res.status(400).json({ error: 'RESTORE_FAILED', message: err.message });
    }
  }

  async getLatestBackup(req, res) {
    try {
      const userId = resolveUserId(req);
      if (!userId) {
        return res.json({ hasBackup: false, backupJson: null });
      }
      const result = await driveBackupService.getLatestBackup(userId);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'GET_LATEST_BACKUP_FAILED', message: err.message });
    }
  }
}

module.exports = new DriveController();
