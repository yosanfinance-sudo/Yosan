const jwt = require('jsonwebtoken');
const config = require('../config');
const db = require('../db');

function optionalAuthMiddleware(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    req.user = null;
    return next();
  }

  const token = authHeader.substring(7);
  try {
    const decoded = jwt.verify(token, config.JWT_SECRET);
    if (decoded && decoded.userId) {
      const user = db.prepare('SELECT id, google_sub, email, name, is_active FROM users WHERE id = ?').get(decoded.userId);
      if (user && user.is_active === 1) {
        req.user = {
          id: user.id,
          googleSub: user.google_sub,
          email: user.email,
          name: user.name
        };
      }
    }
  } catch (err) {
    // Ignore invalid token for optional auth, leave req.user = null
    req.user = null;
  }

  next();
}

module.exports = optionalAuthMiddleware;
