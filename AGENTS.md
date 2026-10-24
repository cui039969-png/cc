# 每天3分钟
- First inspect suitable open-source projects and actual licenses before implementing new capabilities.
- Preserve MIT upstream notices; original additions are Apache-2.0.
- Never copy user records, exports, device databases, secrets, or keystores into this repository.
- Test with synthetic records. Do not read existing real-device app data.
- Keep applicationId io.github.daily3minutes and JSON schemaVersion 1 stable.
- Never use destructive Room fallback; export schemas and test real migrations when schemas change.
- Verify assembleDebug, testDebugUnitTest, lintDebug. Device-dependent results require actual device evidence.
- Do not create a remote repository, push, or publish without separate explicit authorization.
