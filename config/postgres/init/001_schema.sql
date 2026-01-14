-- Rustscape PostgreSQL Schema
-- ============================
-- This script initializes the database schema for the Rustscape game server.
-- It includes tables for user authentication, sessions, player data, and game state.

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================
-- AUTHENTICATION & USER MANAGEMENT
-- ============================================

-- Users table (authentication)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(12) NOT NULL UNIQUE,
    username_lower VARCHAR(12) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash VARCHAR(255) NOT NULL,

    -- Account status
    rights SMALLINT NOT NULL DEFAULT 0,  -- 0=normal, 1=moderator, 2=admin
    is_member BOOLEAN NOT NULL DEFAULT FALSE,
    is_banned BOOLEAN NOT NULL DEFAULT FALSE,
    is_muted BOOLEAN NOT NULL DEFAULT FALSE,
    ban_expires_at TIMESTAMPTZ,
    mute_expires_at TIMESTAMPTZ,
    ban_reason TEXT,

    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ,
    email_verified_at TIMESTAMPTZ,

    -- Security
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ DEFAULT NOW(),

    -- Constraints
    CONSTRAINT username_length CHECK (LENGTH(username) >= 1 AND LENGTH(username) <= 12),
    CONSTRAINT username_format CHECK (username ~ '^[a-zA-Z0-9_]+$'),
    CONSTRAINT email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

-- Index for faster lookups
CREATE INDEX idx_users_username_lower ON users(username_lower);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_last_login ON users(last_login_at);

-- Email verification tokens
CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    used_at TIMESTAMPTZ
);

CREATE INDEX idx_email_tokens_user ON email_verification_tokens(user_id);
CREATE INDEX idx_email_tokens_token ON email_verification_tokens(token);

-- Password reset tokens
CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    used_at TIMESTAMPTZ
);

CREATE INDEX idx_password_tokens_user ON password_reset_tokens(user_id);
CREATE INDEX idx_password_tokens_token ON password_reset_tokens(token);

-- ============================================
-- SESSION MANAGEMENT
-- ============================================

-- Active sessions (for tracking logged-in players)
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_token VARCHAR(128) NOT NULL UNIQUE,

    -- Connection info
    ip_address INET,
    user_agent TEXT,
    world_id SMALLINT,

    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,

    -- Status
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_sessions_user ON sessions(user_id);
CREATE INDEX idx_sessions_token ON sessions(session_token);
CREATE INDEX idx_sessions_active ON sessions(is_active, expires_at);

-- Login history
CREATE TABLE login_history (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ip_address INET,
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_login_history_user ON login_history(user_id);
CREATE INDEX idx_login_history_created ON login_history(created_at);

-- ============================================
-- PLAYER GAME DATA
-- ============================================

-- Player characters (game state)
CREATE TABLE players (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(12) NOT NULL,

    -- Position
    coord_x INT NOT NULL DEFAULT 3222,
    coord_y INT NOT NULL DEFAULT 3222,
    coord_z SMALLINT NOT NULL DEFAULT 0,

    -- Stats
    combat_level SMALLINT NOT NULL DEFAULT 3,
    total_level INT NOT NULL DEFAULT 32,
    total_xp BIGINT NOT NULL DEFAULT 1154,

    -- Appearance
    gender SMALLINT NOT NULL DEFAULT 0,  -- 0=male, 1=female
    appearance JSONB NOT NULL DEFAULT '{}',

    -- Game state
    run_energy INT NOT NULL DEFAULT 10000,
    special_energy INT NOT NULL DEFAULT 1000,

    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    time_played BIGINT NOT NULL DEFAULT 0,  -- seconds

    CONSTRAINT one_player_per_user UNIQUE(user_id)
);

CREATE INDEX idx_players_user ON players(user_id);

-- Player skills
CREATE TABLE player_skills (
    id BIGSERIAL PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    skill_id SMALLINT NOT NULL,
    level SMALLINT NOT NULL DEFAULT 1,
    xp INT NOT NULL DEFAULT 0,

    CONSTRAINT unique_player_skill UNIQUE(player_id, skill_id),
    CONSTRAINT valid_skill_id CHECK (skill_id >= 0 AND skill_id <= 24),
    CONSTRAINT valid_level CHECK (level >= 1 AND level <= 99),
    CONSTRAINT valid_xp CHECK (xp >= 0)
);

CREATE INDEX idx_player_skills_player ON player_skills(player_id);

-- Player inventory
CREATE TABLE player_inventory (
    id BIGSERIAL PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    container_type VARCHAR(20) NOT NULL,  -- 'inventory', 'bank', 'equipment'
    slot INT NOT NULL,
    item_id INT NOT NULL,
    amount INT NOT NULL DEFAULT 1,

    CONSTRAINT unique_container_slot UNIQUE(player_id, container_type, slot),
    CONSTRAINT valid_slot CHECK (slot >= 0),
    CONSTRAINT valid_amount CHECK (amount > 0)
);

CREATE INDEX idx_player_inventory_player ON player_inventory(player_id);
CREATE INDEX idx_player_inventory_container ON player_inventory(player_id, container_type);

-- Player settings/preferences
CREATE TABLE player_settings (
    player_id UUID PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,

    -- Chat settings
    public_chat SMALLINT NOT NULL DEFAULT 0,
    private_chat SMALLINT NOT NULL DEFAULT 0,
    trade_chat SMALLINT NOT NULL DEFAULT 0,

    -- Game settings
    accept_aid BOOLEAN NOT NULL DEFAULT TRUE,
    mouse_buttons SMALLINT NOT NULL DEFAULT 0,
    brightness SMALLINT NOT NULL DEFAULT 2,
    music_volume SMALLINT NOT NULL DEFAULT 127,
    sound_volume SMALLINT NOT NULL DEFAULT 127,

    -- Misc
    settings_json JSONB NOT NULL DEFAULT '{}'
);

-- Friends and ignore lists
CREATE TABLE player_friends (
    id BIGSERIAL PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    friend_name VARCHAR(12) NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT unique_friend UNIQUE(player_id, friend_name)
);

CREATE INDEX idx_player_friends_player ON player_friends(player_id);

CREATE TABLE player_ignores (
    id BIGSERIAL PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    ignored_name VARCHAR(12) NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT unique_ignore UNIQUE(player_id, ignored_name)
);

CREATE INDEX idx_player_ignores_player ON player_ignores(player_id);

-- ============================================
-- WORLD & SERVER STATE
-- ============================================

-- World servers
CREATE TABLE worlds (
    id SMALLINT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    ip_address INET NOT NULL DEFAULT '127.0.0.1',
    port INT NOT NULL DEFAULT 43594,

    -- Status
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    is_members BOOLEAN NOT NULL DEFAULT FALSE,
    player_count INT NOT NULL DEFAULT 0,
    max_players INT NOT NULL DEFAULT 2000,

    -- Region
    country_code VARCHAR(2) NOT NULL DEFAULT 'US',
    region VARCHAR(50),

    -- Timestamps
    last_heartbeat TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Insert default world
INSERT INTO worlds (id, name, ip_address, port, is_online, is_members)
VALUES (1, 'Rustscape', '127.0.0.1', 43596, TRUE, FALSE);

-- ============================================
-- AUDIT & LOGGING
-- ============================================

-- General audit log
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id UUID,
    old_value JSONB,
    new_value JSONB,
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_user ON audit_log(user_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_created ON audit_log(created_at);

-- ============================================
-- FUNCTIONS & TRIGGERS
-- ============================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply updated_at trigger to relevant tables
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_players_updated_at
    BEFORE UPDATE ON players
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Function to create default player data when user is created
CREATE OR REPLACE FUNCTION create_default_player()
RETURNS TRIGGER AS $$
DECLARE
    new_player_id UUID;
BEGIN
    -- Create player record
    INSERT INTO players (user_id, display_name)
    VALUES (NEW.id, NEW.username)
    RETURNING id INTO new_player_id;

    -- Create default skills (all start at level 1, except Hitpoints at 10)
    INSERT INTO player_skills (player_id, skill_id, level, xp)
    SELECT new_player_id, s.skill_id,
           CASE WHEN s.skill_id = 3 THEN 10 ELSE 1 END,  -- skill 3 = Hitpoints
           CASE WHEN s.skill_id = 3 THEN 1154 ELSE 0 END
    FROM generate_series(0, 24) AS s(skill_id);

    -- Create default settings
    INSERT INTO player_settings (player_id)
    VALUES (new_player_id);

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER create_player_on_user_creation
    AFTER INSERT ON users
    FOR EACH ROW
    EXECUTE FUNCTION create_default_player();

-- Function to clean up expired sessions
CREATE OR REPLACE FUNCTION cleanup_expired_sessions()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM sessions
    WHERE expires_at < NOW() OR (is_active = FALSE AND last_activity_at < NOW() - INTERVAL '1 day');
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- DEFAULT DATA
-- ============================================

-- Create a test admin user (password: admin123)
-- In production, create users through the registration system
INSERT INTO users (username, username_lower, email, password_hash, rights, email_verified)
VALUES (
    'admin',
    'admin',
    'admin@rustscape.local',
    crypt('admin123', gen_salt('bf', 12)),
    2,  -- Admin rights
    TRUE
);

-- Create a test regular user (password: test123)
INSERT INTO users (username, username_lower, email, password_hash, rights, email_verified)
VALUES (
    'testuser',
    'testuser',
    'test@rustscape.local',
    crypt('test123', gen_salt('bf', 12)),
    0,  -- Normal user
    TRUE
);

-- ============================================
-- VIEWS
-- ============================================

-- View for online players
CREATE VIEW online_players AS
SELECT
    u.username,
    u.rights,
    p.display_name,
    p.combat_level,
    p.coord_x,
    p.coord_y,
    p.coord_z,
    s.world_id,
    s.last_activity_at
FROM sessions s
JOIN users u ON s.user_id = u.id
JOIN players p ON p.user_id = u.id
WHERE s.is_active = TRUE AND s.expires_at > NOW();

-- View for player stats
CREATE VIEW player_stats_view AS
SELECT
    p.id AS player_id,
    u.username,
    p.display_name,
    p.combat_level,
    p.total_level,
    p.total_xp,
    p.time_played,
    json_object_agg(ps.skill_id, json_build_object('level', ps.level, 'xp', ps.xp)) AS skills
FROM players p
JOIN users u ON p.user_id = u.id
LEFT JOIN player_skills ps ON p.id = ps.player_id
GROUP BY p.id, u.username;

-- ============================================
-- GRANTS (for application user)
-- ============================================

-- If using a separate app user, grant permissions here
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO rustscape_app;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO rustscape_app;
