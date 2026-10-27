const Database = require('better-sqlite3');
const fs = require('fs');
const path = require('path');
const config = require('./config');

fs.mkdirSync(path.dirname(config.DB_PATH), { recursive: true });

const db = new Database(config.DB_PATH);


// Enable foreign key constraints
db.pragma('foreign_keys = ON');

function initSchema() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      google_sub TEXT UNIQUE NOT NULL,
      email TEXT NOT NULL,
      name TEXT NOT NULL,
      profile_picture_url TEXT,
      email_verified INTEGER DEFAULT 1,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL,
      last_login_at INTEGER NOT NULL,
      is_active INTEGER DEFAULT 1
    );

    CREATE TABLE IF NOT EXISTS user_profiles (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER UNIQUE NOT NULL,
      currency_symbol TEXT DEFAULT '₹',
      timezone TEXT DEFAULT 'Asia/Kolkata',
      theme TEXT DEFAULT 'SYSTEM',
      is_biometric_enabled INTEGER DEFAULT 0,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS refresh_tokens (
      token TEXT PRIMARY KEY,
      user_id INTEGER NOT NULL,
      expires_at INTEGER NOT NULL,
      revoked INTEGER DEFAULT 0,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS google_drive_connections (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER UNIQUE NOT NULL,
      google_account_sub TEXT NOT NULL,
      drive_folder_id TEXT,
      encrypted_refresh_token TEXT,
      token_expires_at INTEGER,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL,
      last_backup_at INTEGER,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS accounts (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      account_name TEXT NOT NULL,
      bank_name TEXT DEFAULT '',
      account_number_masked TEXT DEFAULT '',
      account_type TEXT DEFAULT 'BANK',
      opening_balance REAL DEFAULT 0.0,
      current_balance REAL DEFAULT 0.0,
      is_default INTEGER DEFAULT 0,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS categories (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      name TEXT NOT NULL,
      type TEXT NOT NULL,
      icon TEXT DEFAULT 'category',
      color TEXT DEFAULT '#64748B',
      is_default INTEGER DEFAULT 1,
      created_at INTEGER NOT NULL,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS categorization_rules (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      pattern TEXT NOT NULL,
      category_id INTEGER,
      category_name TEXT NOT NULL,
      transaction_type TEXT DEFAULT 'EXPENSE',
      priority INTEGER DEFAULT 10,
      match_type TEXT DEFAULT 'CONTAINS',
      is_active INTEGER DEFAULT 1,
      created_at INTEGER NOT NULL,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS transactions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      account_id INTEGER NOT NULL,
      transaction_date TEXT NOT NULL,
      description TEXT NOT NULL,
      debit_amount REAL DEFAULT 0.0,
      credit_amount REAL DEFAULT 0.0,
      amount REAL DEFAULT 0.0,
      transaction_type TEXT DEFAULT 'EXPENSE',
      balance_after_transaction REAL,
      category_id INTEGER,
      category_name TEXT DEFAULT 'Uncategorized',
      source TEXT DEFAULT 'MANUAL',
      reference_number TEXT DEFAULT '',
      notes TEXT DEFAULT '',
      is_manual INTEGER DEFAULT 1,
      is_categorized INTEGER DEFAULT 0,
      categorization_confidence REAL DEFAULT 0.0,
      transfer_id TEXT,
      linked_loan_id INTEGER,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
      FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS loans (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      borrower_lender_name TEXT NOT NULL,
      loan_type TEXT NOT NULL,
      amount REAL NOT NULL,
      total_repaid REAL DEFAULT 0.0,
      remaining_amount REAL NOT NULL,
      interest_rate REAL DEFAULT 0.0,
      due_date TEXT DEFAULT '',
      notes TEXT DEFAULT '',
      status TEXT DEFAULT 'ACTIVE',
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS loan_repayments (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      loan_id INTEGER NOT NULL,
      amount REAL NOT NULL,
      repayment_date TEXT NOT NULL,
      payment_method TEXT DEFAULT 'CASH',
      notes TEXT DEFAULT '',
      created_at INTEGER NOT NULL,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
      FOREIGN KEY (loan_id) REFERENCES loans(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS company_expenses (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      expense_date TEXT NOT NULL,
      title TEXT NOT NULL,
      amount REAL NOT NULL,
      category_name TEXT DEFAULT 'Official',
      receipt_url TEXT DEFAULT '',
      notes TEXT DEFAULT '',
      is_reimbursed INTEGER DEFAULT 0,
      reimbursement_date TEXT DEFAULT '',
      created_at INTEGER NOT NULL,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS email_verifications (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      email TEXT NOT NULL,
      otp_hash TEXT NOT NULL,
      purpose TEXT NOT NULL,
      user_id INTEGER,
      attempts INTEGER DEFAULT 0,
      expires_at INTEGER NOT NULL,
      verified INTEGER DEFAULT 0,
      created_at INTEGER NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_email_verif_lookup ON email_verifications (email, purpose, verified);

    CREATE TABLE IF NOT EXISTS feedbacks (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER,
      category TEXT NOT NULL,
      rating INTEGER NOT NULL,
      subject TEXT NOT NULL,
      description TEXT NOT NULL,
      app_version TEXT DEFAULT 'v1.1.0',
      device_model TEXT DEFAULT '',
      android_version TEXT DEFAULT '',
      sync_id TEXT UNIQUE,
      created_at INTEGER NOT NULL,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
    );

    CREATE INDEX IF NOT EXISTS idx_feedbacks_user ON feedbacks (user_id);
    CREATE INDEX IF NOT EXISTS idx_feedbacks_sync_id ON feedbacks (sync_id);
  `);

  // Safe migrations for existing database files
  try {
    const driveCols = db.pragma('table_info(google_drive_connections)').map(c => c.name);
    if (!driveCols.includes('drive_email')) {
      db.exec("ALTER TABLE google_drive_connections ADD COLUMN drive_email TEXT DEFAULT ''");
    }
  } catch (err) {
    console.error('Migration check failed (non-critical):', err.message);
  }
}

initSchema();

module.exports = db;
