const aiChatService = require('../services/aiChatService');

class ChatController {
  async handleChat(req, res) {
    try {
      const { message, history, clientContext } = req.body;

      if (!message || typeof message !== 'string' || message.trim().length === 0) {
        return res.status(400).json({
          error: 'INVALID_MESSAGE',
          message: 'Message is required and must be a non-empty string'
        });
      }

      const userId = req.user?.id || 1;
      const result = await aiChatService.processChat(userId, message.trim(), history || [], clientContext);

      return res.json({
        success: true,
        reply: result.reply,
        source: result.source
      });
    } catch (err) {
      console.error('[CHAT_ERROR]', err);
      return res.status(500).json({
        error: 'CHAT_FAILED',
        message: err.message || 'An error occurred while processing your request'
      });
    }
  }
}

module.exports = new ChatController();
