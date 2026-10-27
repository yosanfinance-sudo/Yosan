const jwt = require('jsonwebtoken');
const config = require('../config');
const db = require('../db');

function authMiddleware(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'UNAUTHORIZED', message: 'Missing or invalid Authorization header' });
  }

  const token = authHeader.substring(7);

  try {
    const decoded = jwt.verify(token, config.JWT_SECRET);
    if (!decoded || !decoded.userId) {
      return res.status(401).json({ error: 'INVALID_TOKEN', message: 'Token content is invalid' });
    }

    const user = db.prepare('SELECT id, google_sub, email, name, is_active FROM users WHERE id = ?').get(decoded.userId);
    if (!user || user.is_active !== 1) {
      return res.status(401).json({ error: 'USER_INACTIVE', message: 'User account is inactive or not found' });
    }

    req.user = {
      id: user.id,
      googleSub: user.google_sub,
      email: user.email,
      name: user.name
    };

    next();
  } catch (err) {
    if (err.name === 'TokenExpiredError') {
      return res.status(401).json({ error: 'SESSION_EXPIRED', message: 'Access token has expired' });
    }
    return res.status(401).json({ error: 'INVALID_TOKEN', message: 'Token verification failed' });
  }
}

module.exports = authMiddleware;
