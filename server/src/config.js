const crypto = require('crypto');
const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });

function resolveDbPath() {
  if (!process.env.DB_PATH) {
    return path.resolve(__dirname, '../finance_manager.db');
  }
  if (path.isAbsolute(process.env.DB_PATH)) {
    return process.env.DB_PATH;
  }
  const cleanPath = process.env.DB_PATH.replace(/^\.\/server\//, '').replace(/^server\//, '');
  return path.resolve(__dirname, '../', cleanPath);
}

// Generate cryptographically secure transient secrets for non-production runs when not configured
const transientJwtSecret = crypto.randomBytes(32).toString('hex');
const transientRefreshSecret = crypto.randomBytes(32).toString('hex');

module.exports = {
  PORT: process.env.PORT || 3000,
  NODE_ENV: process.env.NODE_ENV || 'development',
  DATABASE_URL: process.env.DATABASE_URL || '',
  GOOGLE_WEB_CLIENT_ID: process.env.GOOGLE_WEB_CLIENT_ID || '1090000000000-example.apps.googleusercontent.com',
  GOOGLE_ANDROID_CLIENT_ID: process.env.GOOGLE_ANDROID_CLIENT_ID || '',
  GOOGLE_IOS_CLIENT_ID: process.env.GOOGLE_IOS_CLIENT_ID || '',
  GOOGLE_CLIENT_SECRET: process.env.GOOGLE_CLIENT_SECRET || '',
  JWT_SECRET: (() => {
    if (!process.env.JWT_SECRET) {
      if ((process.env.NODE_ENV || 'development') === 'production') {
        throw new Error('JWT_SECRET environment variable must be set in production');
      }
      return transientJwtSecret;
    }
    return process.env.JWT_SECRET;
  })(),
  JWT_REFRESH_SECRET: (() => {
    if (!process.env.JWT_REFRESH_SECRET) {
      if ((process.env.NODE_ENV || 'development') === 'production') {
        throw new Error('JWT_REFRESH_SECRET environment variable must be set in production');
      }
      return transientRefreshSecret;
    }
    return process.env.JWT_REFRESH_SECRET;
  })(),
  JWT_ACCESS_EXPIRES_IN: '15m',
  JWT_REFRESH_EXPIRES_IN: '30d',
  DB_PATH: resolveDbPath()
};
