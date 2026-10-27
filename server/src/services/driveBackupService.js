const fs = require('fs');
const path = require('path');
const { google } = require('googleapis');
const db = require('../db');
const config = require('../config');
const crypto = require('crypto');
const driveRepository = require('../repositories/driveRepository');

// Utility for encryption/decryption at rest (Section 21)
const ENCRYPTION_KEY = crypto.createHash('sha256').update(config.JWT_SECRET).digest();
const IV_LENGTH = 16;

function encryptToken(text) {
  if (!text) return '';
  const iv = crypto.randomBytes(IV_LENGTH);
  const cipher = crypto.createCipheriv('aes-256-cbc', ENCRYPTION_KEY, iv);
  let encrypted = cipher.update(text);
  encrypted = Buffer.concat([encrypted, cipher.final()]);
  return iv.toString('hex') + ':' + encrypted.toString('hex');
}

function decryptToken(text) {
  if (!text) return '';
  const textParts = text.split(':');
  if (textParts.length !== 2) throw new Error('MALFORMED_ENCRYPTED_TOKEN');
  const iv = Buffer.from(textParts[0], 'hex');
  const encryptedText = Buffer.from(textParts[1], 'hex');
  const decipher = crypto.createDecipheriv('aes-256-cbc', ENCRYPTION_KEY, iv);
  let decrypted = decipher.update(encryptedText);
  decrypted = Buffer.concat([decrypted, decipher.final()]);
  return decrypted.toString();
}

function getOAuth2Client() {
  return new google.auth.OAuth2(
    config.GOOGLE_CLIENT_ID || config.GOOGLE_WEB_CLIENT_ID,
    config.GOOGLE_CLIENT_SECRET,
    'postmessage'
  );
}

function getDriveClientForUser(userId) {
  const conn = driveRepository.getDriveConnection(userId);
  if (!conn || !conn.encrypted_refresh_token) {
    throw new Error('DRIVE_NOT_CONNECTED');
  }

  const refreshToken = decryptToken(conn.encrypted_refresh_token);
  const oauth2Client = getOAuth2Client();
  oauth2Client.setCredentials({ refresh_token: refreshToken });

  return {
    drive: google.drive({ version: 'v3', auth: oauth2Client }),
    conn
  };
}

async function connectDrive(userId, authCode, googleAccountSub, driveEmail) {
  const userRepository = require('../repositories/userRepository');
  const jwt = require('jsonwebtoken');
  const user = userRepository.getUserById(userId);
  if (!user) {
    throw new Error('USER_NOT_FOUND: User not found');
  }

  const now = Date.now();
  let refreshToken = '';
  let resolvedDriveEmail = (driveEmail || '').trim().toLowerCase();

  if (authCode) {
    try {
      const oauth2Client = getOAuth2Client();
      const { tokens } = await oauth2Client.getToken(authCode);
      refreshToken = tokens.refresh_token || tokens.access_token || '';

      if (tokens.id_token) {
        try {
          const decoded = jwt.decode(tokens.id_token);
          if (decoded && decoded.email) {
            resolvedDriveEmail = decoded.email.trim().toLowerCase();
          }
        } catch (_) {}
      }
    } catch (e) {
      refreshToken = authCode;
    }
  }

  if (!refreshToken) {
    throw new Error('DRIVE_OAUTH_TOKEN_MISSING: Could not obtain a valid Drive refresh token');
  }

  // Email Validation Check:
  const userEmail = (user.email || '').trim().toLowerCase();
  if (userEmail && resolvedDriveEmail && userEmail !== resolvedDriveEmail) {
    throw new Error(`EMAIL_MISMATCH: Google Drive account (${resolvedDriveEmail}) does not match your registered email (${userEmail}).`);
  }

  const finalDriveEmail = resolvedDriveEmail || userEmail;
  const encrypted = encryptToken(refreshToken);

  driveRepository.insertDriveConnection(userId, googleAccountSub || 'sub', '', encrypted, finalDriveEmail, now);

  return {
    success: true,
    message: 'Google Drive connected successfully',
    driveEmail: finalDriveEmail
  };
}

async function disconnectDrive(userId) {
  driveRepository.deleteDriveConnection(userId);
  return { success: true, message: 'Google Drive disconnected' };
}

function calculateChecksum(obj) {
  const str = typeof obj === 'string' ? obj : JSON.stringify(obj);
  return crypto.createHash('sha256').update(str).digest('hex');
}

function formatVersionFilename(version) {
  const vNum = parseInt(version, 10) || 1;
  return `backup-v${String(vNum).padStart(4, '0')}.json`;
}

function getLocalBackupDir(userId) {
  const localDir = path.join(__dirname, '..', '..', 'drive_storage', String(userId), 'Yosan-Backup');
  const versionsDir = path.join(localDir, 'versions');
  fs.mkdirSync(versionsDir, { recursive: true });
  return { localDir, versionsDir };
}

function getDriveStatus(userId) {
  const conn = driveRepository.getDriveConnection(userId);
  if (!conn) {
    return { isConnected: false, driveEmail: null, lastBackupAt: null, currentBackupVersion: null };
  }

  let currentVersion = 1;
  try {
    const { localDir } = getLocalBackupDir(userId);
    const detailsPath = path.join(localDir, 'detailsaboutbackup.json');
    if (fs.existsSync(detailsPath)) {
      const parsed = JSON.parse(fs.readFileSync(detailsPath, 'utf8'));
      if (parsed.backup_version) currentVersion = parsed.backup_version;
    }
  } catch (_) {}

  return {
    isConnected: true,
    googleAccountSub: conn.google_account_sub,
    driveEmail: conn.drive_email || null,
    driveFolderId: conn.drive_folder_id || 'Yosan-Backup',
    lastBackupAt: conn.last_backup_at,
    currentBackupVersion: currentVersion
  };
}

function generateBackupPayload(userId) {
  const accounts = db.prepare('SELECT * FROM accounts WHERE user_id = ?').all(userId);
  const categories = db.prepare('SELECT * FROM categories WHERE user_id = ?').all(userId);
  const rules = db.prepare('SELECT * FROM categorization_rules WHERE user_id = ?').all(userId);
  const transactions = db.prepare('SELECT * FROM transactions WHERE user_id = ?').all(userId);
  const loans = db.prepare('SELECT * FROM loans WHERE user_id = ?').all(userId);
  const loanRepayments = db.prepare('SELECT * FROM loan_repayments WHERE user_id = ?').all(userId);
  const companyExpenses = db.prepare('SELECT * FROM company_expenses WHERE user_id = ?').all(userId);

  const payload = {
    application: 'Yosan Finance',
    backup_id: crypto.randomUUID(),
    version: 1,
    backup_version: 1,
    schema_version: 1,
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
    transaction_count: transactions.length,
    checksum: '',
    accounts,
    categories,
    rules,
    transactions,
    loans,
    loanRepayments,
    companyExpenses
  };
  payload.checksum = calculateChecksum(transactions);
  return payload;
}

async function findOrCreateFolder(drive, folderName, parentId = null) {
  let q = `name = '${folderName}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false`;
  if (parentId) {
    q += ` and '${parentId}' in parents`;
  }
  const res = await drive.files.list({ q, fields: 'files(id, name)' });
  if (res.data.files && res.data.files.length > 0) {
    return res.data.files[0].id;
  }
  const fileMetadata = {
    name: folderName,
    mimeType: 'application/vnd.google-apps.folder',
    parents: parentId ? [parentId] : []
  };
  const folder = await drive.files.create({ resource: fileMetadata, fields: 'id' });
  return folder.data.id;
}

async function uploadBackup(userId, clientPayload = null) {
  const now = Date.now();
  let payload = clientPayload;

  if (!payload || typeof payload !== 'object') {
    payload = generateBackupPayload(userId);
  }

  // Ensure standard envelope fields
  payload.application = payload.application || 'Yosan Finance';
  payload.backup_id = payload.backup_id || crypto.randomUUID();
  payload.schema_version = payload.schema_version || 1;
  payload.version = payload.version || payload.backup_version || 1;
  payload.backup_version = payload.backup_version || payload.version || 1;
  payload.updated_at = new Date().toISOString();
  if (!payload.created_at) payload.created_at = payload.updated_at;

  const txList = Array.isArray(payload.transactions) ? payload.transactions : [];
  payload.transaction_count = txList.length;
  payload.checksum = calculateChecksum(txList.length > 0 ? txList : payload);

  const { drive, conn } = getDriveClientForUser(userId);
  const refreshToken = decryptToken(conn.encrypted_refresh_token);

  // 1. Mock / Local Dev Mode
  if (refreshToken.startsWith('mock_')) {
    const { localDir, versionsDir } = getLocalBackupDir(userId);
    const detailsPath = path.join(localDir, 'detailsaboutbackup.json');

    if (fs.existsSync(detailsPath)) {
      try {
        const existingRaw = fs.readFileSync(detailsPath, 'utf8');
        const existing = JSON.parse(existingRaw);

        if (existing.checksum && existing.checksum === payload.checksum) {
          // Identical backup data, safe retry without duplicate snapshot
          driveRepository.updateLastBackup(userId, now);
          return {
            success: true,
            fileId: 'mock_detailsaboutbackup',
            fileName: 'detailsaboutbackup.json',
            version: existing.backup_version || 1,
            backupDate: existing.updated_at || payload.updated_at,
            transactionCount: payload.transaction_count,
            message: 'Backup verified (no changes detected since last backup)'
          };
        }

        // Previous version snapshot
        const prevVersion = existing.backup_version || 1;
        const snapshotName = formatVersionFilename(prevVersion);
        fs.writeFileSync(path.join(versionsDir, snapshotName), JSON.stringify(existing, null, 2), 'utf8');

        // Increment version
        payload.backup_version = prevVersion + 1;
        payload.version = payload.backup_version;
        fs.writeFileSync(detailsPath, JSON.stringify(payload, null, 2), 'utf8');
        driveRepository.updateLastBackup(userId, now);

        return {
          success: true,
          fileId: 'mock_detailsaboutbackup',
          fileName: 'detailsaboutbackup.json',
          version: payload.backup_version,
          backupDate: payload.updated_at,
          transactionCount: payload.transaction_count,
          message: `Backup version ${payload.backup_version} created successfully`
        };
      } catch (err) {
        console.error('Error handling mock previous backup, replacing:', err);
      }
    }

    // First backup
    payload.backup_version = 1;
    payload.version = 1;
    fs.writeFileSync(detailsPath, JSON.stringify(payload, null, 2), 'utf8');
    driveRepository.updateLastBackup(userId, now);

    return {
      success: true,
      fileId: 'mock_detailsaboutbackup',
      fileName: 'detailsaboutbackup.json',
      version: 1,
      backupDate: payload.updated_at,
      transactionCount: payload.transaction_count,
      message: 'First backup created successfully (version 1)'
    };
  }

  // 2. Real Google Drive API Mode
  try {
    // 2a. Find or create root folder 'Yosan-Backup'
    let rootFolderId = conn.drive_folder_id;
    if (!rootFolderId) {
      rootFolderId = await findOrCreateFolder(drive, 'Yosan-Backup');
      driveRepository.updateFolderId(userId, rootFolderId, now);
    }

    // 2b. Find or create 'versions' folder inside 'Yosan-Backup'
    const versionsFolderId = await findOrCreateFolder(drive, 'versions', rootFolderId);

    // 2c. Check for existing 'detailsaboutbackup.json' in 'Yosan-Backup'
    const detailsQ = `'${rootFolderId}' in parents and name = 'detailsaboutbackup.json' and trashed = false`;
    const detailsRes = await drive.files.list({ q: detailsQ, fields: 'files(id, name, createdTime)' });
    const existingFile = detailsRes.data.files && detailsRes.data.files[0];

    if (existingFile) {
      try {
        const fileContentRes = await drive.files.get({ fileId: existingFile.id, alt: 'media' });
        const existingData = typeof fileContentRes.data === 'string' ? JSON.parse(fileContentRes.data) : fileContentRes.data;

        if (existingData && existingData.checksum && existingData.checksum === payload.checksum) {
          // Identical checksum: avoid duplicate snapshot
          driveRepository.updateLastBackup(userId, now);
          return {
            success: true,
            fileId: existingFile.id,
            fileName: 'detailsaboutbackup.json',
            version: existingData.backup_version || 1,
            backupDate: existingData.updated_at || payload.updated_at,
            transactionCount: payload.transaction_count,
            message: 'Backup verified (no changes detected since last backup)'
          };
        }

        // Snapshot existing data into versions/
        const prevVersion = (existingData && existingData.backup_version) || 1;
        const snapshotName = formatVersionFilename(prevVersion);
        const snapshotMeta = { name: snapshotName, parents: [versionsFolderId] };
        const snapshotMedia = { mimeType: 'application/json', body: JSON.stringify(existingData, null, 2) };
        await drive.files.create({ resource: snapshotMeta, media: snapshotMedia, fields: 'id' });

        // Update detailsaboutbackup.json with incremented version
        payload.backup_version = prevVersion + 1;
        payload.version = payload.backup_version;
        const updateMedia = { mimeType: 'application/json', body: JSON.stringify(payload, null, 2) };
        await drive.files.update({ fileId: existingFile.id, media: updateMedia, fields: 'id, name, modifiedTime' });

        driveRepository.updateLastBackup(userId, now);
        return {
          success: true,
          fileId: existingFile.id,
          fileName: 'detailsaboutbackup.json',
          version: payload.backup_version,
          backupDate: payload.updated_at,
          transactionCount: payload.transaction_count,
          message: `Backup version ${payload.backup_version} created successfully`
        };
      } catch (readErr) {
        console.warn('Could not read existing detailsaboutbackup.json, will overwrite as version 1:', readErr.message);
      }
    }

    // First backup in Drive
    payload.backup_version = 1;
    payload.version = 1;
    const fileMetadata = { name: 'detailsaboutbackup.json', parents: [rootFolderId] };
    const media = { mimeType: 'application/json', body: JSON.stringify(payload, null, 2) };
    const created = await drive.files.create({ resource: fileMetadata, media, fields: 'id, name, createdTime' });

    driveRepository.updateLastBackup(userId, now);

    return {
      success: true,
      fileId: created.data.id,
      fileName: 'detailsaboutbackup.json',
      version: 1,
      backupDate: payload.updated_at,
      transactionCount: payload.transaction_count,
      message: 'First backup created successfully (version 1)'
    };
  } catch (err) {
    throw err;
  }
}

async function listBackups(userId) {
  const result = [];
  const conn = driveRepository.getDriveConnection(userId);
  if (!conn) return result;

  const refreshToken = decryptToken(conn.encrypted_refresh_token);

  // 1. Mock / Local Dev Mode
  if (refreshToken.startsWith('mock_')) {
    const { localDir, versionsDir } = getLocalBackupDir(userId);
    const detailsPath = path.join(localDir, 'detailsaboutbackup.json');

    if (fs.existsSync(detailsPath)) {
      try {
        const details = JSON.parse(fs.readFileSync(detailsPath, 'utf8'));
        result.push({
          version: details.backup_version || 1,
          fileName: 'detailsaboutbackup.json',
          fileId: 'mock_detailsaboutbackup',
          createdTime: details.updated_at || details.created_at || new Date().toISOString(),
          transactionCount: details.transaction_count || (details.transactions ? details.transactions.length : 0),
          isCurrent: true,
          checksum: details.checksum || ''
        });
      } catch (_) {}
    }

    if (fs.existsSync(versionsDir)) {
      const files = fs.readdirSync(versionsDir).filter(f => f.startsWith('backup-v') && f.endsWith('.json'));
      for (const f of files) {
        try {
          const content = JSON.parse(fs.readFileSync(path.join(versionsDir, f), 'utf8'));
          result.push({
            version: content.backup_version || parseInt(f.replace('backup-v', '').replace('.json', ''), 10) || 1,
            fileName: f,
            fileId: `mock_${f}`,
            createdTime: content.updated_at || content.created_at || new Date().toISOString(),
            transactionCount: content.transaction_count || (content.transactions ? content.transactions.length : 0),
            isCurrent: false,
            checksum: content.checksum || ''
          });
        } catch (_) {}
      }
    }

    result.sort((a, b) => (b.version || 0) - (a.version || 0));
    return result;
  }

  // 2. Real Drive API Mode
  try {
    const { drive } = getDriveClientForUser(userId);
    const rootFolderId = conn.drive_folder_id;
    if (!rootFolderId) return result;

    // Get current detailsaboutbackup.json
    const detailsQ = `'${rootFolderId}' in parents and name = 'detailsaboutbackup.json' and trashed = false`;
    const detailsRes = await drive.files.list({ q: detailsQ, fields: 'files(id, name, createdTime, modifiedTime)' });
    if (detailsRes.data.files && detailsRes.data.files.length > 0) {
      const f = detailsRes.data.files[0];
      try {
        const mediaRes = await drive.files.get({ fileId: f.id, alt: 'media' });
        const parsed = typeof mediaRes.data === 'string' ? JSON.parse(mediaRes.data) : mediaRes.data;
        result.push({
          version: parsed.backup_version || 1,
          fileName: 'detailsaboutbackup.json',
          fileId: f.id,
          createdTime: f.modifiedTime || f.createdTime,
          transactionCount: parsed.transaction_count || (parsed.transactions ? parsed.transactions.length : 0),
          isCurrent: true,
          checksum: parsed.checksum || ''
        });
      } catch (_) {}
    }

    // Get versions
    const versionsFolderId = await findOrCreateFolder(drive, 'versions', rootFolderId);
    const versionsQ = `'${versionsFolderId}' in parents and name contains 'backup-v' and trashed = false`;
    const versionsRes = await drive.files.list({
      q: versionsQ,
      fields: 'files(id, name, createdTime, modifiedTime)',
      orderBy: 'createdTime desc'
    });

    if (versionsRes.data.files) {
      for (const vf of versionsRes.data.files) {
        const vNum = parseInt(vf.name.replace('backup-v', '').replace('.json', ''), 10) || 1;
        result.push({
          version: vNum,
          fileName: vf.name,
          fileId: vf.id,
          createdTime: vf.createdTime || vf.modifiedTime,
          transactionCount: 0,
          isCurrent: false,
          checksum: ''
        });
      }
    }

    result.sort((a, b) => (b.version || 0) - (a.version || 0));
    return result;
  } catch (err) {
    console.error('Error listing Drive backups:', err);
    return result;
  }
}

async function getBackupVersionPayload(userId, version) {
  const conn = driveRepository.getDriveConnection(userId);
  if (!conn) throw new Error('DRIVE_NOT_CONNECTED');

  const refreshToken = decryptToken(conn.encrypted_refresh_token);
  const targetVersion = parseInt(version, 10);

  // 1. Mock / Local Dev Mode
  if (refreshToken.startsWith('mock_')) {
    const { localDir, versionsDir } = getLocalBackupDir(userId);
    const detailsPath = path.join(localDir, 'detailsaboutbackup.json');

    if (fs.existsSync(detailsPath)) {
      const details = JSON.parse(fs.readFileSync(detailsPath, 'utf8'));
      if (details.backup_version === targetVersion || !targetVersion) {
        return details;
      }
    }

    if (targetVersion) {
      const snapshotFile = path.join(versionsDir, formatVersionFilename(targetVersion));
      if (fs.existsSync(snapshotFile)) {
        return JSON.parse(fs.readFileSync(snapshotFile, 'utf8'));
      }
    }

    throw new Error(`BACKUP_VERSION_NOT_FOUND: Version ${version} not found`);
  }

  // 2. Real Google Drive
  const { drive } = getDriveClientForUser(userId);
  const rootFolderId = conn.drive_folder_id;
  if (!rootFolderId) throw new Error('DRIVE_FOLDER_NOT_FOUND');

  if (!targetVersion) {
    const detailsQ = `'${rootFolderId}' in parents and name = 'detailsaboutbackup.json' and trashed = false`;
    const res = await drive.files.list({ q: detailsQ, fields: 'files(id, name)' });
    if (res.data.files && res.data.files.length > 0) {
      const fileId = res.data.files[0].id;
      const media = await drive.files.get({ fileId, alt: 'media' });
      return typeof media.data === 'string' ? JSON.parse(media.data) : media.data;
    }
  } else {
    // Check if current version matches target
    const detailsQ = `'${rootFolderId}' in parents and name = 'detailsaboutbackup.json' and trashed = false`;
    const res = await drive.files.list({ q: detailsQ, fields: 'files(id, name)' });
    if (res.data.files && res.data.files.length > 0) {
      const media = await drive.files.get({ fileId: res.data.files[0].id, alt: 'media' });
      const current = typeof media.data === 'string' ? JSON.parse(media.data) : media.data;
      if (current.backup_version === targetVersion) return current;
    }

    // Look in versions folder
    const versionsFolderId = await findOrCreateFolder(drive, 'versions', rootFolderId);
    const targetName = formatVersionFilename(targetVersion);
    const vQ = `'${versionsFolderId}' in parents and name = '${targetName}' and trashed = false`;
    const vRes = await drive.files.list({ q: vQ, fields: 'files(id, name)' });
    if (vRes.data.files && vRes.data.files.length > 0) {
      const vMedia = await drive.files.get({ fileId: vRes.data.files[0].id, alt: 'media' });
      return typeof vMedia.data === 'string' ? JSON.parse(vMedia.data) : vMedia.data;
    }
  }

  throw new Error(`BACKUP_VERSION_NOT_FOUND: Version ${version} not found in Google Drive`);
}

function restoreBackupData(userId, data) {
  if (!data || (!data.version && !data.backup_version && !data.schema_version && !data.application)) {
    throw new Error('INVALID_BACKUP_FORMAT');
  }

  const now = Date.now();
  const tx = db.transaction(() => {
    // Purge user data safely (scoped to userId)
    db.prepare('DELETE FROM transactions WHERE user_id = ?').run(userId);
    db.prepare('DELETE FROM loan_repayments WHERE user_id = ?').run(userId);
    db.prepare('DELETE FROM loans WHERE user_id = ?').run(userId);
    db.prepare('DELETE FROM company_expenses WHERE user_id = ?').run(userId);
    db.prepare('DELETE FROM categorization_rules WHERE user_id = ?').run(userId);
    db.prepare('DELETE FROM categories WHERE user_id = ?').run(userId);
    db.prepare('DELETE FROM accounts WHERE user_id = ?').run(userId);

    // Restore accounts
    if (Array.isArray(data.accounts)) {
      const stmtWithId = db.prepare(`
        INSERT OR REPLACE INTO accounts (id, user_id, account_name, bank_name, account_number_masked, account_type, opening_balance, current_balance, is_default, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      const stmtNoId = db.prepare(`
        INSERT INTO accounts (user_id, account_name, bank_name, account_number_masked, account_type, opening_balance, current_balance, is_default, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const a of data.accounts) {
        if (a.id) {
          stmtWithId.run(a.id, userId, a.account_name || a.accountName, a.bank_name || a.bankName || '', a.account_number_masked || a.accountNumberMasked || '', a.account_type || a.accountType || 'BANK', a.opening_balance || a.openingBalance || 0, a.current_balance || a.currentBalance || 0, a.is_default || a.isDefault ? 1 : 0, now, now);
        } else {
          stmtNoId.run(userId, a.account_name || a.accountName, a.bank_name || a.bankName || '', a.account_number_masked || a.accountNumberMasked || '', a.account_type || a.accountType || 'BANK', a.opening_balance || a.openingBalance || 0, a.current_balance || a.currentBalance || 0, a.is_default || a.isDefault ? 1 : 0, now, now);
        }
      }
    }

    // Restore categories
    if (Array.isArray(data.categories)) {
      const stmtWithId = db.prepare(`
        INSERT OR REPLACE INTO categories (id, user_id, name, type, icon, color, is_default, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `);
      const stmtNoId = db.prepare(`
        INSERT INTO categories (user_id, name, type, icon, color, is_default, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `);
      for (const c of data.categories) {
        if (c.id) {
          stmtWithId.run(c.id, userId, c.name, c.type, c.icon || 'category', c.color || '#64748B', c.is_default || c.isDefault ? 1 : 0, now);
        } else {
          stmtNoId.run(userId, c.name, c.type, c.icon || 'category', c.color || '#64748B', c.is_default || c.isDefault ? 1 : 0, now);
        }
      }
    }

    // Restore rules
    if (Array.isArray(data.rules)) {
      const stmt = db.prepare(`
        INSERT INTO categorization_rules (user_id, pattern, category_id, category_name, transaction_type, priority, match_type, is_active, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const r of data.rules) {
        stmt.run(userId, r.pattern || r.keyword, r.category_id || r.categoryId || null, r.category_name || r.categoryName, r.transaction_type || r.transactionType || 'EXPENSE', r.priority || 10, r.match_type || r.matchType || 'CONTAINS', r.is_active || r.isActive ? 1 : 0, now);
      }
    }

    // Restore transactions
    const firstAccount = db.prepare('SELECT id FROM accounts WHERE user_id = ? LIMIT 1').get(userId);
    const defaultAccId = firstAccount ? firstAccount.id : 1;

    if (Array.isArray(data.transactions)) {
      const stmt = db.prepare(`
        INSERT INTO transactions (user_id, account_id, transaction_date, description, debit_amount, credit_amount, amount, transaction_type, balance_after_transaction, category_id, category_name, source, reference_number, notes, is_manual, is_categorized, categorization_confidence, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const t of data.transactions) {
        const rawAccId = t.account_id || t.accountId;
        const validAccount = rawAccId ? db.prepare('SELECT id FROM accounts WHERE id = ?').get(rawAccId) : null;
        const targetAccId = validAccount ? validAccount.id : defaultAccId;

        stmt.run(
          userId,
          targetAccId,
          t.transaction_date || t.transactionDate || '2026-01-01',
          t.description || 'Restored Transaction',
          t.debit_amount || t.debitAmount || 0,
          t.credit_amount || t.creditAmount || 0,
          t.amount || 0,
          t.transaction_type || t.transactionType || 'EXPENSE',
          t.balance_after_transaction || t.balanceAfterTransaction || null,
          t.category_id || t.categoryId || null,
          t.category_name || t.categoryName || 'Uncategorized',
          t.source || 'RESTORE',
          t.reference_number || t.referenceNumber || '',
          t.notes || '',
          t.is_manual || t.isManual ? 1 : 0,
          t.is_categorized || t.isCategorized ? 1 : 0,
          t.categorization_confidence || t.categorizationConfidence || 1.0,
          now,
          now
        );
      }
    }

    // Restore loans
    if (Array.isArray(data.loans)) {
      const stmt = db.prepare(`
        INSERT OR REPLACE INTO loans (id, user_id, borrower_lender_name, loan_type, amount, total_repaid, remaining_amount, interest_rate, due_date, notes, status, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const l of data.loans) {
        stmt.run(
          l.id || null,
          userId,
          l.borrower_lender_name || l.borrowerLenderName || 'Unknown',
          l.loan_type || l.loanType || 'GIVEN',
          l.amount || 0,
          l.total_repaid || l.totalRepaid || 0,
          l.remaining_amount || l.remainingAmount || l.amount || 0,
          l.interest_rate || l.interestRate || 0,
          l.due_date || l.dueDate || '',
          l.notes || '',
          l.status || 'ACTIVE',
          l.created_at || l.createdAt || now,
          l.updated_at || l.updatedAt || now
        );
      }
    }

    // Restore loan repayments
    if (Array.isArray(data.loanRepayments)) {
      const stmt = db.prepare(`
        INSERT OR REPLACE INTO loan_repayments (id, user_id, loan_id, amount, repayment_date, payment_method, notes, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const r of data.loanRepayments) {
        stmt.run(
          r.id || null,
          userId,
          r.loan_id || r.loanId,
          r.amount || 0,
          r.repayment_date || r.repaymentDate || new Date(now).toISOString().split('T')[0],
          r.payment_method || r.paymentMethod || 'CASH',
          r.notes || '',
          r.created_at || r.createdAt || now
        );
      }
    }

  });

  tx();
  return { success: true, message: 'Data restored successfully' };
}

async function getLatestBackup(userId) {
  const accounts = db.prepare('SELECT COUNT(*) as count FROM accounts WHERE user_id = ?').get(userId);
  const transactions = db.prepare('SELECT COUNT(*) as count FROM transactions WHERE user_id = ?').get(userId);
  const conn = driveRepository.getDriveConnection(userId);

  if ((accounts && accounts.count > 0) || (transactions && transactions.count > 0) || (conn && conn.last_backup_at)) {
    const payload = generateBackupPayload(userId);
    return {
      hasBackup: true,
      backupDate: conn?.last_backup_at ? new Date(conn.last_backup_at).toISOString() : new Date().toISOString(),
      accountsCount: accounts?.count || 0,
      transactionsCount: transactions?.count || 0,
      backupJson: JSON.stringify(payload)
    };
  }

  if (conn && conn.drive_folder_id) {
    try {
      const files = await listBackups(userId);
      if (files && files.length > 0) {
        const latestFile = files[0];
        const refreshToken = decryptToken(conn.encrypted_refresh_token);
        if (refreshToken.startsWith('mock_')) {
          const { localDir } = getLocalBackupDir(userId);
          const detailsPath = path.join(localDir, 'detailsaboutbackup.json');
          if (fs.existsSync(detailsPath)) {
            const data = JSON.parse(fs.readFileSync(detailsPath, 'utf8'));
            return {
              hasBackup: true,
              backupDate: latestFile.createdTime || new Date().toISOString(),
              accountsCount: (data.accounts || []).length,
              transactionsCount: (data.transactions || []).length,
              backupJson: JSON.stringify(data)
            };
          }
        }
        const { drive } = getDriveClientForUser(userId);
        const res = await drive.files.get({ fileId: latestFile.fileId || latestFile.id, alt: 'media' });
        const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
        return {
          hasBackup: true,
          backupDate: latestFile.createdTime || new Date().toISOString(),
          accountsCount: (data.accounts || []).length,
          transactionsCount: (data.transactions || []).length,
          backupJson: JSON.stringify(data)
        };
      }
    } catch (_) {}
  }

  return {
    hasBackup: false
  };
}

module.exports = {
  connectDrive,
  disconnectDrive,
  getDriveStatus,
  generateBackupPayload,
  uploadBackup,
  listBackups,
  restoreBackupData,
  getLatestBackup,
  getBackupVersionPayload
};
