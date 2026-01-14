//! Integration tests for auth flow and player persistence
//!
//! These tests verify the end-to-end behavior of:
//! - User authentication (both in-memory and database-backed)
//! - Player persistence (load/save)
//! - User ID plumbing between auth and persistence

use rustscape_server::auth::{AuthResult, AuthService};
use rustscape_server::game::persistence::{PlayerData, Position};
use uuid::Uuid;

/// Test that AuthService correctly authenticates users in dev mode
#[test]
fn test_auth_service_dev_mode() {
    let auth = AuthService::new(true); // dev mode

    // In dev mode, any credentials should work
    let result = auth.authenticate("testuser", "anypassword");
    assert!(result.is_ok(), "Dev mode should accept any credentials");

    let auth_result = result.unwrap();
    assert_eq!(auth_result.account.username, "testuser");
    assert_eq!(auth_result.account.rights, 2); // Admin in dev mode
    assert!(auth_result.player_index > 0);

    // user_id should be None in dev mode (no database)
    assert!(auth_result.user_id().is_none());
}

/// Test that AuthService correctly tracks player indices
#[test]
fn test_auth_service_player_indices() {
    let auth = AuthService::with_max_players(true, 5);

    // Authenticate 5 users
    let results: Vec<AuthResult> = (1..=5)
        .map(|i| {
            auth.authenticate(&format!("user{}", i), "pass")
                .expect("Should authenticate")
        })
        .collect();

    // All should have unique indices
    let indices: Vec<u16> = results.iter().map(|r| r.player_index).collect();
    let unique: std::collections::HashSet<_> = indices.iter().collect();
    assert_eq!(unique.len(), 5, "All player indices should be unique");

    // 6th user should fail (world full)
    let result = auth.authenticate("user6", "pass");
    assert!(result.is_err(), "Should fail when world is full");

    // Release one index
    auth.release_player_index(results[2].player_index);

    // Now should succeed
    let result = auth.authenticate("user6", "pass");
    assert!(result.is_ok(), "Should succeed after releasing an index");
}

/// Test that Account correctly stores user_id when created with database info
#[test]
fn test_account_user_id() {
    use rustscape_server::auth::Account;

    let user_id = Uuid::new_v4();
    let account = Account::with_user_id(1, user_id, "testuser", "hash".to_string());

    assert_eq!(account.user_id, Some(user_id));
    assert_eq!(account.username, "testuser");
    assert_eq!(account.id, 1);
}

/// Test AuthResult correctly exposes user_id
#[test]
fn test_auth_result_user_id() {
    use rustscape_server::auth::Account;

    let user_id = Uuid::new_v4();
    let account = Account::with_user_id(1, user_id, "testuser", "hash".to_string());

    let auth_result = AuthResult {
        account,
        player_index: 1,
    };

    assert_eq!(auth_result.user_id(), Some(user_id));
}

/// Test PlayerData creation and basic operations
#[test]
fn test_player_data_creation() {
    let user_id = Uuid::new_v4();
    let player_data = PlayerData::new(user_id, "TestPlayer".to_string());

    assert_eq!(player_data.user_id, user_id);
    assert_eq!(player_data.display_name, "TestPlayer");
    assert_eq!(player_data.combat_level, 3); // Default combat level
    assert_eq!(player_data.skills.len(), 25); // All skills

    // Check default position (Lumbridge)
    assert_eq!(player_data.position.x, 3222);
    assert_eq!(player_data.position.y, 3222);
    assert_eq!(player_data.position.z, 0);
}

/// Test PlayerData XP and level calculations
#[test]
fn test_player_data_xp_system() {
    let user_id = Uuid::new_v4();
    let mut player_data = PlayerData::new(user_id, "TestPlayer".to_string());

    // Add some XP to Attack (skill 0)
    let leveled_up = player_data.add_xp(0, 1000);

    // Should have leveled up from 1 - add_xp returns Option<(old_level, new_level)>
    assert!(leveled_up.is_some(), "Should level up from XP gain");

    // Check the skill was updated
    let attack_skill = player_data.get_skill(0).expect("Attack skill should exist");
    assert!(attack_skill.xp >= 1000);
    assert!(attack_skill.level > 1);
}

/// Test Position default spawn location
#[test]
fn test_position_default_spawn() {
    let pos = Position::default_spawn();

    assert_eq!(pos.x, 3222);
    assert_eq!(pos.y, 3222);
    assert_eq!(pos.z, 0);
}

/// Test that AuthService has_database returns correct value
#[test]
fn test_auth_service_has_database() {
    let auth_without_db = AuthService::new(false);
    assert!(
        !auth_without_db.has_database(),
        "Should not have database without pool"
    );

    // Note: We can't easily test with_database without a real pool
    // That would require a database connection
}

/// Test username normalization in authentication
#[test]
fn test_auth_username_normalization() {
    let auth = AuthService::new(true);

    // Authenticate with mixed case
    let result1 = auth.authenticate("TestUser", "pass").unwrap();
    assert_eq!(result1.account.username, "testuser");

    // Try to authenticate again with same normalized username
    // This should fail because already logged in (same account)
    // Actually in dev mode, it creates new accounts each time
    // Let's verify the normalization at least
    let result2 = auth.authenticate("TESTUSER", "pass").unwrap();
    assert_eq!(result2.account.username, "testuser");
}

/// Test that production mode auth rejects unknown users
#[test]
fn test_auth_production_mode_rejects_unknown() {
    let auth = AuthService::new(false); // Production mode

    // Unknown user should be rejected
    let result = auth.authenticate("unknownuser", "password");
    assert!(
        result.is_err(),
        "Production mode should reject unknown users"
    );
}

/// Test registration in production mode
#[test]
fn test_auth_registration() {
    let auth = AuthService::new(false);

    // Register a new user
    let account = auth.register("newuser", "password123");
    assert!(account.is_ok(), "Registration should succeed");

    let account = account.unwrap();
    assert_eq!(account.username, "newuser");
    assert_eq!(account.rights, 0); // Normal user

    // Now authentication should succeed
    let result = auth.authenticate("newuser", "password123");
    assert!(result.is_ok(), "Should authenticate registered user");
}

/// Test duplicate registration prevention
#[test]
fn test_auth_duplicate_registration() {
    let auth = AuthService::new(false);

    // Register first user with a longer password (>= 4 chars required)
    let result1 = auth.register("dupuser", "password1");
    assert!(
        result1.is_ok(),
        "First registration should succeed: {:?}",
        result1.err()
    );

    // Try to register same username
    let result2 = auth.register("dupuser", "password2");
    assert!(result2.is_err(), "Should reject duplicate username");

    // Case-insensitive check
    let result3 = auth.register("DupUser", "password3");
    assert!(
        result3.is_err(),
        "Should reject case-insensitive duplicate username"
    );
}

/// Test account disable functionality
#[test]
fn test_auth_account_disable() {
    let auth = AuthService::new(false);

    // Register and authenticate
    auth.register("disabletest", "password").unwrap();
    let result = auth.authenticate("disabletest", "password");
    assert!(result.is_ok());

    // Release the index from the first auth
    auth.release_player_index(result.unwrap().player_index);

    // Disable the account
    auth.set_enabled("disabletest", false).unwrap();

    // Should now fail to authenticate
    let result = auth.authenticate("disabletest", "password");
    assert!(result.is_err(), "Disabled account should not authenticate");
}

/// Test account rights modification
#[test]
fn test_auth_rights_modification() {
    let auth = AuthService::new(false);

    // Register a normal user
    auth.register("rightstest", "password").unwrap();

    let account = auth.get_account("rightstest").unwrap();
    assert_eq!(account.rights, 0);

    // Promote to moderator
    auth.set_rights("rightstest", 1).unwrap();

    let account = auth.get_account("rightstest").unwrap();
    assert_eq!(account.rights, 1);

    // Promote to admin
    auth.set_rights("rightstest", 2).unwrap();

    let account = auth.get_account("rightstest").unwrap();
    assert_eq!(account.rights, 2);
}

// Note: Full database integration tests would require:
// 1. A running PostgreSQL instance
// 2. The schema to be applied
// 3. Test isolation (transactions or cleanup)
//
// These would typically be run separately with:
// cargo test --features integration -- --ignored
//
// For now, we test the non-DB parts thoroughly.

#[cfg(test)]
mod player_tests {
    use rustscape_server::game::persistence::PlayerData;
    use rustscape_server::game::player::{Location, Player, PlayerRights, Skills};
    use uuid::Uuid;

    #[test]
    fn test_player_creation() {
        let player = Player::new(1, 12345, "testplayer".to_string());

        assert_eq!(player.index, 1);
        assert_eq!(player.session_id, 12345);
        assert_eq!(player.username, "testplayer");
        assert_eq!(player.display_name, "testplayer"); // underscores replaced with spaces
        assert!(player.user_id.is_none()); // No database user ID
    }

    #[test]
    fn test_player_from_player_data() {
        let user_id = Uuid::new_v4();
        let player_data = PlayerData::new(user_id, "Test Player".to_string());

        let player = Player::from_player_data(
            1,
            12345,
            &player_data,
            PlayerRights::Normal,
            false, // not member
        );

        assert_eq!(player.index, 1);
        assert_eq!(player.session_id, 12345);
        assert_eq!(player.user_id, Some(user_id)); // Should have user_id from PlayerData
        assert_eq!(player.display_name, "Test Player");
    }

    #[test]
    fn test_player_to_player_data_roundtrip() {
        let user_id = Uuid::new_v4();
        let player_id = Uuid::new_v4();

        // Create player from data
        let original_data = PlayerData::new(user_id, "RoundTrip".to_string());
        let player = Player::from_player_data(1, 12345, &original_data, PlayerRights::Normal, true);

        // Convert back to PlayerData
        let converted_data = player.to_player_data(player_id, user_id);

        // Check key fields match
        assert_eq!(converted_data.id, player_id);
        assert_eq!(converted_data.user_id, user_id);
        assert_eq!(converted_data.display_name, "RoundTrip");
    }

    #[test]
    fn test_location_operations() {
        let loc1 = Location::new(3222, 3222, 0);
        let loc2 = Location::new(3225, 3222, 0);

        // Distance calculation (returns f64)
        let distance = loc1.distance_to(&loc2);
        assert!(
            (distance - 3.0).abs() < 0.001,
            "Distance should be approximately 3"
        );

        // Within distance check
        assert!(loc1.within_distance(&loc2, 5));
        assert!(!loc1.within_distance(&loc2, 2));

        // Region calculations
        assert_eq!(loc1.region_x(), 50); // 3222 / 64 = 50
        assert_eq!(loc1.region_y(), 50);
    }

    #[test]
    fn test_skills_combat_level() {
        let skills = Skills::default();

        // Default skills should give combat level 3
        let combat_level = skills.combat_level();
        assert_eq!(combat_level, 3);
    }
}
