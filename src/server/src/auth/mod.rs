//! Authentication service module
//!
//! Provides authentication and account management for the game server.
//! Supports both development mode (accepts all logins) and production mode
//! (validates against stored credentials).

use std::collections::HashMap;
use std::sync::RwLock;

use argon2::{
    password_hash::{rand_core::OsRng, PasswordHash, PasswordHasher, PasswordVerifier, SaltString},
    Argon2,
};
use tracing::{debug, info, warn};

use crate::error::{AuthError, Result, RustscapeError};

/// Player account information
#[derive(Debug, Clone)]
pub struct Account {
    /// Unique account ID
    pub id: u64,
    /// Username (normalized)
    pub username: String,
    /// Password hash (Argon2)
    pub password_hash: String,
    /// Player rights (0=normal, 1=mod, 2=admin)
    pub rights: u8,
    /// Member status
    pub member: bool,
    /// Whether the account is enabled
    pub enabled: bool,
    /// Whether the account is locked (too many failed attempts)
    pub locked: bool,
}

impl Account {
    /// Create a new account with the given credentials
    pub fn new(id: u64, username: &str, password: &str) -> Result<Self> {
        let password_hash = hash_password(password)?;
        Ok(Self {
            id,
            username: normalize_username(username),
            password_hash,
            rights: 0,
            member: false,
            enabled: true,
            locked: false,
        })
    }

    /// Create a development account (no password hashing)
    pub fn dev_account(id: u64, username: &str) -> Self {
        Self {
            id,
            username: normalize_username(username),
            password_hash: String::new(),
            rights: 2, // Admin in dev mode
            member: true,
            enabled: true,
            locked: false,
        }
    }

    /// Verify the password against the stored hash
    pub fn verify_password(&self, password: &str) -> bool {
        if self.password_hash.is_empty() {
            // Dev account - no password check
            return true;
        }
        verify_password(password, &self.password_hash)
    }
}

/// Authentication result containing account info and player index
#[derive(Debug, Clone)]
pub struct AuthResult {
    /// Account information
    pub account: Account,
    /// Assigned player index (1-2047)
    pub player_index: u16,
}

/// Authentication service for managing player accounts and logins
pub struct AuthService {
    /// Whether running in development mode
    dev_mode: bool,
    /// In-memory account storage (username -> account)
    accounts: RwLock<HashMap<String, Account>>,
    /// Next available account ID
    next_id: RwLock<u64>,
    /// Active player indices (to avoid duplicates)
    active_indices: RwLock<Vec<bool>>,
    /// Maximum number of players
    max_players: u16,
}

impl AuthService {
    /// Create a new authentication service
    pub fn new(dev_mode: bool) -> Self {
        let max_players = 2000;
        Self {
            dev_mode,
            accounts: RwLock::new(HashMap::new()),
            next_id: RwLock::new(1),
            active_indices: RwLock::new(vec![false; max_players as usize + 1]),
            max_players,
        }
    }

    /// Create with custom max players
    pub fn with_max_players(dev_mode: bool, max_players: u16) -> Self {
        Self {
            dev_mode,
            accounts: RwLock::new(HashMap::new()),
            next_id: RwLock::new(1),
            active_indices: RwLock::new(vec![false; max_players as usize + 1]),
            max_players,
        }
    }

    /// Authenticate a user with username and password
    pub fn authenticate(&self, username: &str, password: &str) -> Result<AuthResult> {
        let username_normalized = normalize_username(username);

        if self.dev_mode {
            // In dev mode, accept any login and create account on the fly
            debug!(
                username = %username_normalized,
                "Dev mode authentication - auto-accepting"
            );

            let account = self.get_or_create_dev_account(&username_normalized);
            let player_index = self.allocate_player_index()?;

            return Ok(AuthResult {
                account,
                player_index,
            });
        }

        // Production mode - verify against stored accounts
        let accounts = self.accounts.read().unwrap();
        let account = accounts
            .get(&username_normalized)
            .ok_or(RustscapeError::Auth(AuthError::InvalidCredentials))?;

        // Check if account is enabled
        if !account.enabled {
            return Err(RustscapeError::Auth(AuthError::AccountDisabled));
        }

        // Check if account is locked
        if account.locked {
            return Err(RustscapeError::Auth(AuthError::AccountLocked));
        }

        // Verify password
        if !account.verify_password(password) {
            warn!(username = %username_normalized, "Failed login attempt");
            return Err(RustscapeError::Auth(AuthError::InvalidCredentials));
        }

        let account = account.clone();
        drop(accounts); // Release read lock before allocating index

        // Allocate player index
        let player_index = self.allocate_player_index()?;

        info!(
            username = %username_normalized,
            player_index = player_index,
            "Authentication successful"
        );

        Ok(AuthResult {
            account,
            player_index,
        })
    }

    /// Register a new account
    pub fn register(&self, username: &str, password: &str) -> Result<Account> {
        let username_normalized = normalize_username(username);

        // Validate username
        if username_normalized.is_empty() || username_normalized.len() > 12 {
            return Err(RustscapeError::Auth(AuthError::InvalidUsername));
        }

        // Validate password
        if password.len() < 4 || password.len() > 20 {
            return Err(RustscapeError::Auth(AuthError::InvalidPassword));
        }

        let mut accounts = self.accounts.write().unwrap();

        // Check if username already exists
        if accounts.contains_key(&username_normalized) {
            return Err(RustscapeError::Auth(AuthError::RegistrationFailed(
                "Username already exists".to_string(),
            )));
        }

        // Get next account ID
        let id = {
            let mut next_id = self.next_id.write().unwrap();
            let id = *next_id;
            *next_id += 1;
            id
        };

        // Create account
        let account = Account::new(id, &username_normalized, password)?;
        accounts.insert(username_normalized.clone(), account.clone());

        info!(
            username = %username_normalized,
            account_id = id,
            "New account registered"
        );

        Ok(account)
    }

    /// Get or create a development account
    fn get_or_create_dev_account(&self, username: &str) -> Account {
        let mut accounts = self.accounts.write().unwrap();

        if let Some(account) = accounts.get(username) {
            return account.clone();
        }

        // Create new dev account
        let id = {
            let mut next_id = self.next_id.write().unwrap();
            let id = *next_id;
            *next_id += 1;
            id
        };

        let account = Account::dev_account(id, username);
        accounts.insert(username.to_string(), account.clone());

        debug!(
            username = %username,
            account_id = id,
            "Created dev account"
        );

        account
    }

    /// Allocate a player index (1-2047)
    fn allocate_player_index(&self) -> Result<u16> {
        let mut indices = self.active_indices.write().unwrap();

        // Find first available index (start at 1, 0 is reserved)
        for i in 1..=self.max_players as usize {
            if !indices[i] {
                indices[i] = true;
                return Ok(i as u16);
            }
        }

        Err(RustscapeError::Auth(AuthError::WorldFull))
    }

    /// Release a player index when they log out
    pub fn release_player_index(&self, index: u16) {
        if index > 0 && (index as usize) <= self.max_players as usize {
            let mut indices = self.active_indices.write().unwrap();
            indices[index as usize] = false;
            debug!(player_index = index, "Released player index");
        }
    }

    /// Get account by username (for admin purposes)
    pub fn get_account(&self, username: &str) -> Option<Account> {
        let accounts = self.accounts.read().unwrap();
        accounts.get(&normalize_username(username)).cloned()
    }

    /// Update account rights
    pub fn set_rights(&self, username: &str, rights: u8) -> Result<()> {
        let mut accounts = self.accounts.write().unwrap();
        let username_normalized = normalize_username(username);

        if let Some(account) = accounts.get_mut(&username_normalized) {
            account.rights = rights;
            info!(
                username = %username_normalized,
                rights = rights,
                "Updated account rights"
            );
            Ok(())
        } else {
            Err(RustscapeError::Auth(AuthError::InvalidCredentials))
        }
    }

    /// Set account enabled status
    pub fn set_enabled(&self, username: &str, enabled: bool) -> Result<()> {
        let mut accounts = self.accounts.write().unwrap();
        let username_normalized = normalize_username(username);

        if let Some(account) = accounts.get_mut(&username_normalized) {
            account.enabled = enabled;
            info!(
                username = %username_normalized,
                enabled = enabled,
                "Updated account status"
            );
            Ok(())
        } else {
            Err(RustscapeError::Auth(AuthError::InvalidCredentials))
        }
    }

    /// Get count of registered accounts
    pub fn account_count(&self) -> usize {
        self.accounts.read().unwrap().len()
    }

    /// Get count of active players
    pub fn active_player_count(&self) -> usize {
        self.active_indices
            .read()
            .unwrap()
            .iter()
            .filter(|&&active| active)
            .count()
    }

    /// Check if running in dev mode
    pub fn is_dev_mode(&self) -> bool {
        self.dev_mode
    }
}

/// Normalize a username (lowercase, trim, replace spaces with underscores)
pub fn normalize_username(username: &str) -> String {
    username.trim().to_lowercase().replace(' ', "_")
}

/// Hash a password using Argon2
fn hash_password(password: &str) -> Result<String> {
    let salt = SaltString::generate(&mut OsRng);
    let argon2 = Argon2::default();

    let password_hash = argon2
        .hash_password(password.as_bytes(), &salt)
        .map_err(|e| {
            RustscapeError::Internal(format!("Failed to hash password: {}", e))
        })?
        .to_string();

    Ok(password_hash)
}

/// Verify a password against a hash
fn verify_password(password: &str, hash: &str) -> bool {
    let parsed_hash = match PasswordHash::new(hash) {
        Ok(h) => h,
        Err(_) => return false,
    };

    Argon2::default()
        .verify_password(password.as_bytes(), &parsed_hash)
        .is_ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_normalize_username() {
        assert_eq!(normalize_username("Player"), "player");
        assert_eq!(normalize_username("  Test User  "), "test_user");
        assert_eq!(normalize_username("UPPER_CASE"), "upper_case");
    }

    #[test]
    fn test_dev_mode_auth() {
        let auth = AuthService::new(true);

        let result = auth.authenticate("test_user", "any_password");
        assert!(result.is_ok());

        let auth_result = result.unwrap();
        assert_eq!(auth_result.account.username, "test_user");
        assert_eq!(auth_result.account.rights, 2); // Admin in dev mode
        assert!(auth_result.player_index > 0);
    }

    #[test]
    fn test_register_and_auth() {
        let auth = AuthService::new(false);

        // Register
        let account = auth.register("newuser", "password123");
        assert!(account.is_ok());

        // Auth with correct password
        let result = auth.authenticate("newuser", "password123");
        assert!(result.is_ok());

        // Auth with wrong password
        let result = auth.authenticate("newuser", "wrongpassword");
        assert!(result.is_err());
    }

    #[test]
    fn test_duplicate_registration() {
        let auth = AuthService::new(false);

        let result1 = auth.register("testuser", "password123");
        assert!(result1.is_ok());

        let result2 = auth.register("testuser", "different");
        assert!(result2.is_err());
    }

    #[test]
    fn test_player_index_allocation() {
        let auth = AuthService::with_max_players(true, 3);

        let result1 = auth.authenticate("user1", "pass");
        assert!(result1.is_ok());
        assert_eq!(result1.unwrap().player_index, 1);

        let result2 = auth.authenticate("user2", "pass");
        assert!(result2.is_ok());
        assert_eq!(result2.unwrap().player_index, 2);

        let result3 = auth.authenticate("user3", "pass");
        assert!(result3.is_ok());
        assert_eq!(result3.unwrap().player_index, 3);

        // World should be full now
        let result4 = auth.authenticate("user4", "pass");
        assert!(result4.is_err());
    }

    #[test]
    fn test_release_player_index() {
        let auth = AuthService::with_max_players(true, 2);

        let result1 = auth.authenticate("user1", "pass").unwrap();
        let result2 = auth.authenticate("user2", "pass").unwrap();

        // World full
        assert!(auth.authenticate("user3", "pass").is_err());

        // Release an index
        auth.release_player_index(result1.player_index);

        // Now we can add another
        let result3 = auth.authenticate("user3", "pass");
        assert!(result3.is_ok());
        assert_eq!(result3.unwrap().player_index, result1.player_index);
    }

    #[test]
    fn test_account_disabled() {
        let auth = AuthService::new(false);

        auth.register("testuser", "password123").unwrap();
        auth.set_enabled("testuser", false).unwrap();

        let result = auth.authenticate("testuser", "password123");
        assert!(matches!(
            result,
            Err(RustscapeError::Auth(AuthError::AccountDisabled))
        ));
    }

    #[test]
    fn test_set_rights() {
        let auth = AuthService::new(false);

        auth.register("testuser", "password123").unwrap();
        auth.set_rights("testuser", 2).unwrap();

        let account = auth.get_account("testuser").unwrap();
        assert_eq!(account.rights, 2);
    }

    #[test]
    fn test_password_hashing() {
        let password = "test_password_123";
        let hash = hash_password(password).unwrap();

        assert!(verify_password(password, &hash));
        assert!(!verify_password("wrong_password", &hash));
    }

    #[test]
    fn test_invalid_username() {
        let auth = AuthService::new(false);

        // Too long
        let result = auth.register("verylongusername", "password123");
        assert!(result.is_err());

        // Empty
        let result = auth.register("", "password123");
        assert!(result.is_err());
    }

    #[test]
    fn test_invalid_password() {
        let auth = AuthService::new(false);

        // Too short
        let result = auth.register("testuser", "abc");
        assert!(result.is_err());

        // Too long
        let result = auth.register("testuser", "a]".repeat(15).as_str());
        assert!(result.is_err());
    }
}
