const db = require('../db');

class DriveRepository {
  getDriveConnection(userId) {
    return db.prepare('SELECT * FROM google_drive_connections WHERE user_id = ?').get(userId);
  }

  insertDriveConnection(userId, googleAccountSub, driveFolderId, encryptedRefreshToken, driveEmail, now) {
    return db.prepare(`
      INSERT INTO google_drive_connections (user_id, google_account_sub, drive_folder_id, encrypted_refresh_token, drive_email, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(user_id) DO UPDATE SET
        google_account_sub = excluded.google_account_sub,
        encrypted_refresh_token = excluded.encrypted_refresh_token,
        drive_email = excluded.drive_email,
        updated_at = excluded.updated_at
    `).run(userId, googleAccountSub || 'sub', driveFolderId || '', encryptedRefreshToken, driveEmail || '', now, now);
  }

  deleteDriveConnection(userId) {
    return db.prepare('DELETE FROM google_drive_connections WHERE user_id = ?').run(userId);
  }

  updateFolderId(userId, folderId, now) {
    return db.prepare('UPDATE google_drive_connections SET drive_folder_id = ?, updated_at = ? WHERE user_id = ?').run(folderId, now, userId);
  }

  updateLastBackup(userId, now) {
    return db.prepare('UPDATE google_drive_connections SET last_backup_at = ?, updated_at = ? WHERE user_id = ?').run(now, now, userId);
  }
}

module.exports = new DriveRepository();
