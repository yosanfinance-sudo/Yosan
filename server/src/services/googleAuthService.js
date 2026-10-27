const { OAuth2Client } = require('google-auth-library');
const jwt = require('jsonwebtoken');
const config = require('../config');
const db = require('../db');
const userRepository = require('../repositories/userRepository');

const client = new OAuth2Client(config.GOOGLE_WEB_CLIENT_ID);

async function verifyGoogleToken(idToken) {
  if (!idToken) {
    throw new Error('ID_TOKEN_REQUIRED');
  }

  // Handle local mock/dev testing tokens if explicitly formatted
  if (idToken.startsWith('mock_id_token_')) {
    if (config.NODE_ENV === 'production') {
      throw new Error('MOCK_TOKENS_DISABLED_IN_PRODUCTION');
    }
    const parts = idToken.split('_');
    const sub = parts[parts.length - 2] || '109123456789012345678';
    const username = parts[parts.length - 1] || 'user';
    return {
      sub,
      email: `${username}@gmail.com`,
      name: username,
      picture: 'https://lh3.googleusercontent.com/a/default-user',
      emailVerified: true
    };
  }

  try {
    const ticket = await client.verifyIdToken({
      idToken,
      audience: [
        config.GOOGLE_WEB_CLIENT_ID,
        config.GOOGLE_ANDROID_CLIENT_ID,
        config.GOOGLE_IOS_CLIENT_ID
      ].filter(Boolean)
    });
    const payload = ticket.getPayload();
    
    if (!payload || !payload.sub) {
      throw new Error('INVALID_GOOGLE_PAYLOAD');
    }

    return {
      sub: payload.sub,
      email: payload.email || '',
      name: payload.name || payload.email || 'User',
      picture: payload.picture || '',
      emailVerified: payload.email_verified ?? true
    };
  } catch (err) {
    throw new Error('INVALID_GOOGLE_TOKEN');
  }
}

function generateTokens(user) {
  const payload = {
    userId: user.id,
    googleSub: user.google_sub,
    email: user.email
  };

  const accessToken = jwt.sign(payload, config.JWT_SECRET, {
    expiresIn: config.JWT_ACCESS_EXPIRES_IN
  });

  const refreshToken = jwt.sign(payload, config.JWT_REFRESH_SECRET, {
    expiresIn: config.JWT_REFRESH_EXPIRES_IN
  });

  return { accessToken, refreshToken };
}

async function handleGoogleAuth(idToken) {
  const claims = await verifyGoogleToken(idToken);
  const now = Date.now();

  let user = userRepository.getUserByGoogleSub(claims.sub);
  let isNewUser = false;

  if (user) {
    // Update existing user last_login_at
    userRepository.updateLastLogin(user.id, now);
    user = userRepository.getUserById(user.id);
  } else {
    // Create new user
    isNewUser = true;
    const result = userRepository.createUser(
      claims.sub,
      claims.email,
      claims.name,
      claims.picture,
      claims.emailVerified,
      now
    );

    const userId = result.lastInsertRowid;
    user = userRepository.getUserById(userId);

    // Initialize user profile settings (Section 12)
    userRepository.createUserProfile(userId, now);

    // Seed default categories (Section 12)
    seedDefaultCategories(userId, now);
  }

  const tokens = generateTokens(user);
  const expiresAt = Date.now() + 30 * 24 * 60 * 60 * 1000;
  userRepository.insertRefreshToken(tokens.refreshToken, user.id, expiresAt);

  const profile = userRepository.getUserProfile(user.id);

  return {
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
      theme: profile?.theme || 'SYSTEM'
    },
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken
  };
}

function seedDefaultCategories(userId, now) {
  const defaultCategories = [
    { name: 'Food & Dining', type: 'EXPENSE', icon: 'restaurant', color: '#EF4444' },
    { name: 'Groceries', type: 'EXPENSE', icon: 'shopping_cart', color: '#F59E0B' },
    { name: 'Shopping', type: 'EXPENSE', icon: 'shopping_bag', color: '#EC4899' },
    { name: 'Transportation', type: 'EXPENSE', icon: 'directions_bus', color: '#3B82F6' },
    { name: 'Fuel', type: 'EXPENSE', icon: 'local_gas_station', color: '#6366F1' },
    { name: 'Bills & Utilities', type: 'EXPENSE', icon: 'receipt', color: '#8B5CF6' },
    { name: 'Rent', type: 'EXPENSE', icon: 'home', color: '#10B981' },
    { name: 'Entertainment', type: 'EXPENSE', icon: 'movie', color: '#84CC16' },
    { name: 'Health & Fitness', type: 'EXPENSE', icon: 'fitness_center', color: '#06B6D4' },
    { name: 'Travel', type: 'EXPENSE', icon: 'flight', color: '#0EA5E9' },
    { name: 'Education', type: 'EXPENSE', icon: 'school', color: '#A855F7' },
    { name: 'Investments', type: 'EXPENSE', icon: 'trending_up', color: '#14B8A6' },
    { name: 'Salary', type: 'INCOME', icon: 'payments', color: '#22C55E' },
    { name: 'Freelance', type: 'INCOME', icon: 'work', color: '#10B981' },
    { name: 'Investment Income', type: 'INCOME', icon: 'show_chart', color: '#059669' },
    { name: 'Gift / Grants', type: 'INCOME', icon: 'card_giftcard', color: '#064E3B' },
    { name: 'Other Income', type: 'INCOME', icon: 'account_balance_wallet', color: '#15803D' }
  ];

  const stmt = db.prepare(`
    INSERT INTO categories (user_id, name, type, icon, color, is_default, created_at)
    VALUES (?, ?, ?, ?, ?, 1, ?)
  `);

  for (const cat of defaultCategories) {
    stmt.run(userId, cat.name, cat.type, cat.icon, cat.color, now);
  }
}


module.exports = {
  verifyGoogleToken,
  handleGoogleAuth,
  generateTokens,
  seedDefaultCategories
};
